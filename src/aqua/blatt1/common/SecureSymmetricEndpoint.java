package aqua.blatt1.common;

import messaging.Endpoint;
import messaging.Message;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.HashMap;
import java.util.Map;

public class SecureSymmetricEndpoint extends Endpoint {

	private Endpoint endpoint;
	private Cipher decrypt;
	private Cipher encrypt;
	private KeyPair keyPair;
	Map<InetSocketAddress, Key> keys = new HashMap<>();

	public SecureSymmetricEndpoint(int port){

		endpoint = new Endpoint(port);
		initializeEndpoint();
	}

	public SecureSymmetricEndpoint() {

		endpoint = new Endpoint();
		initializeEndpoint();

	}

	private void initializeEndpoint(){
		SecretKeySpec keySpec;
		keySpec = new SecretKeySpec("CAFEBABECAFEBABE".getBytes(),"AES"); //Excercise 1



		try {
			decrypt = Cipher.getInstance("AES");
			encrypt = Cipher.getInstance("AES");
			decrypt.init(Cipher.DECRYPT_MODE,keySpec);
			encrypt.init(Cipher.ENCRYPT_MODE,keySpec);

		} catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException e) {
			e.printStackTrace();
		}
	}



	@Override
	public void send(InetSocketAddress address, Serializable payload){

		try {
			SealedObject s = new SealedObject(payload,encrypt);
			//endpoint.send(address,decrypt.doFinal((byte[]) payload));
			endpoint.send(address,s);
		} catch (IllegalBlockSizeException | IOException e) {
			e.printStackTrace();
		}
	}

	private Message decrypt(Message crypto){
		SealedObject cryptoPayload = (SealedObject) crypto.getPayload();
		try {
			Serializable serializable = (Serializable) cryptoPayload.getObject(decrypt);

			return new Message(serializable,crypto.getSender());
		} catch (IllegalBlockSizeException | ClassNotFoundException | IOException | BadPaddingException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Message blockingReceive() {
		Message crypto = endpoint.blockingReceive();
		return decrypt(crypto);
	}

	@Override
	public Message nonBlockingReceive() {
		Message crypto = endpoint.nonBlockingReceive();
		return decrypt(crypto);
	}

}
