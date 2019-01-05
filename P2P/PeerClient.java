
import java.net.*;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerClient extends Thread {

	Socket requestSocket;
	BufferedOutputStream outputStream;
	BufferedInputStream inputStream;
	boolean isClient;
	String peerID;
	byte[] peerBitField;
	boolean interestedPeer = true;
	boolean chokedPeer = true;
	AtomicBoolean killProcess = new AtomicBoolean(false);
	Float dwldRate = 1.0f;
	ConfigReader configReader;

	private byte[] mergeArrayByte(byte[] firstArray, byte secondByte) {

		byte[] res = new byte[firstArray.length + 1];

		System.arraycopy(firstArray, 0, res, 0, firstArray.length);

		res[firstArray.length] = secondByte;

		return res;

	}
	private byte[] mergeByte(byte[] firstArray, byte[] secondArray) {

		byte[] result = new byte[firstArray.length + secondArray.length];

		System.arraycopy(firstArray, 0, result, 0, firstArray.length);

		System.arraycopy(secondArray, 0, result, firstArray.length, secondArray.length);

		return result;

	}
	private byte[] intToByte(int id) {

		byte[] conversion = new byte[4];

		conversion[0] = (byte) ((id >> 24) & 0xFF);

		conversion[1] = (byte) ((id >> 16) & 0xFF);

		conversion[2] = (byte) ((id >> 8) & 0xFF);

		conversion[3] = (byte) (id & 0xFF);

		return conversion;

	}
	public byte[] chokeMessage() {
		peerProcess.logger.info("Constructing CHOKE Message");
		byte[] len = intToByte(1);
		byte integerToByte = intToByte(MessageTypes.choke.ordinal())[3];
		byte[] res = mergeArrayByte(len, integerToByte);
		return res;
	}
	
	public byte[] unChokeMessage() {
		peerProcess.logger.info("Constructing UNCHOKE Message");
		byte[] len = intToByte(1);
		byte integerToByte = intToByte(MessageTypes.unchoke.ordinal())[3];
		byte[] res = mergeArrayByte(len, integerToByte);
		return res;
	}
	public byte[] interestedMessage() {
		peerProcess.logger.info("Constructing INTERESTED Message");
		byte[] len = intToByte(1);
		byte integerToByte = intToByte(MessageTypes.interested.ordinal())[3];
		byte[] res = mergeArrayByte(len, integerToByte);
		return res;
	}
	public byte[] notInterestedMessage() {
		peerProcess.logger.info("Constructing NOTINTERESTED Message");
		byte[] len = intToByte(1);
		byte integerToByte = intToByte(MessageTypes.not_interested.ordinal())[3];
		byte[] res = mergeArrayByte(len, integerToByte);
		return res;
	}

	public byte[] haveMessage(byte[] pieceIndex) {
		peerProcess.logger.info("Constructing HAVE message");
		byte[] len = intToByte(5);
		byte integerToByte = intToByte(MessageTypes.have.ordinal())[3];
		byte[] res = mergeByte(mergeArrayByte(len, integerToByte), pieceIndex);
		return res;
	}
	int messageLength;//4Bytes
	MessageTypes messageType;//1Byte
	byte[] messagePayload;//VariableLength
	
	public void setMessage(MessageTypes messageType, byte[] payLoad) {
		this.messageLength = payLoad.length;
		this.messageType = messageType;
		this.messagePayload = payLoad;
	}
	

	public byte[] getByteMessage() {
		Integer msgLength = messageLength + 1;
		byte[] len = intToByte(msgLength);
		byte integerToByte = intToByte(messageType.ordinal())[3];
		byte[] res = mergeByte(mergeArrayByte(len, integerToByte), messagePayload);
		return res;
	}

	public byte[] bitFieldMessage(byte[] payload) {
		peerProcess.logger.info("Constructing BITFIELD Message");
		setMessage(MessageTypes.bitfield, payload);
		return getByteMessage();
	}

	public byte[] requestMessage(int index) {
		peerProcess.logger.info("Constructing REQUEST Message");
		 setMessage(MessageTypes.request, intToByte(index));
		return getByteMessage();
	}

	public byte[] pieceMessage(int idx, byte[] payload) {
		peerProcess.logger.info("Constructing PIECE Message");
		setMessage(MessageTypes.piece,
				mergeByte(intToByte(idx), payload));
		return getByteMessage();
	}
	public PeerClient(Socket socket, boolean isPeerClient, String peerID, ConfigReader confReader) {
	
		this.configReader = confReader;
		this.requestSocket = socket;
		this.isClient = isPeerClient;
		try {
			outputStream = new BufferedOutputStream(requestSocket.getOutputStream());
			outputStream.flush();
			inputStream = new BufferedInputStream(requestSocket.getInputStream());
		
			if (isPeerClient) {
				initiateClient(peerID);
			} else {
				initiateServer();
			}
			
			openComm();
		
		} catch (IOException ex) {
			ex.printStackTrace();
			peerProcess.logger.info("Exception: " + ex.toString());
		}
	}
	private int byteToInt(byte[] value) {

		int conversion0 = ((value[0] & 0xFF) << 24);
		int conversion1 = ((value[1] & 0xFF) << 16);
		int conversion2 = ((value[2] & 0xFF) << 8);
		int conversion3 = (value[3] & 0xFF);

		return conversion0 | conversion1 | conversion2 | conversion3;

	}

	public void run() {
		try {
			long procTime = 0l;
			long totTime = 0l;
			
			byte[] msgLength, msgType;
			msgType = new byte[1];
			msgLength = new byte[4];
			
			int requestedIndex = 0;
			int receivedPiecesCount = 0;

			while (!killProcess.get()) {

				inputStream.read(msgLength);
				inputStream.read(msgType);
				int ordinal = new BigInteger(msgType).intValue();
				MessageTypes messageType = MessageTypes.values()[ordinal];
					
				
				if(messageType.equals(MessageTypes.choke)) {
					peerProcess.logger.info("Peer: " + peerProcess.peerProcessID + " is choked by Peer: " + peerID);
					byte byteIndex = peerProcess.bitField[requestedIndex / 8];
					if (((1 << (7 - (requestedIndex % 8))) & byteIndex) == 0) {
						peerProcess.requestedPiecesFromPeers[requestedIndex].set(false);
					}

					
				}
				else if(messageType.equals(MessageTypes.unchoke)){
					peerProcess.logger.info("Peer: " + peerProcess.peerProcessID + " is unchoked by Peer:" + peerID);
					int pieceIndex = requestedPIndex(peerProcess.bitField, peerBitField, peerProcess.requestedPiecesFromPeers);
					if (pieceIndex >= 0) {
						requestedIndex = pieceIndex;
						peerProcess.requestedPiecesFromPeers[pieceIndex].set(true);
						sendMessageToPeer(requestMessage(pieceIndex));
						procTime = System.nanoTime();
					}else {
						
					}

					
				}
				else if(messageType.equals(MessageTypes.interested)) {
					peerProcess.logger.info("Peer " + peerProcess.peerProcessID + " received the 'interested' message from " + peerID + ".");
					interestedPeer = true;
				}
				else if(messageType.equals(MessageTypes.not_interested)) {
									
					peerProcess.logger
					.info("Peer " + peerProcess.peerProcessID + " received the 'not interested' message from Peer: " + peerID);
					interestedPeer = false;
					chokedPeer = true;
				}
				else if(messageType.equals(MessageTypes.have)) {
					byte[] pieceBytes = readMsgPayload(inputStream, 4);
					int pIndPosition = byteToInt(pieceBytes);
					peerProcess.logger.info("Peer: " + peerProcess.peerProcessID + " received the 'have' message from Peer: " + peerID
							+ " for the piece index:" + pIndPosition);
					
					peerBitField[pIndPosition / 8] |= (1 << (7 - (pIndPosition % 8)));
					byte indexByte = peerProcess.bitField[pIndPosition / 8];
					if (((1 << (7 - (pIndPosition % 8))) & indexByte) == 0) {
						sendMessageToPeer(interestedMessage());
					} else {
						sendMessageToPeer(notInterestedMessage());
					}
					
				}
				else if(messageType.equals(MessageTypes.request)) {
					
					byte[] requestPayload = readMsgPayload(inputStream, 4);
					
					int pieceIndex = byteToInt(requestPayload);
					
					peerProcess.logger.info("Peer: " + peerProcess.peerProcessID + " received the 'request' message from Peer: " + peerID
							+ " for the pieceIndex: " + pieceIndex);
					int startIndex = pieceIndex * ConfigReader.PieceSize;
					
					try {
						byte[] resourceData;
					
						if ((ConfigReader.fileSize - startIndex) < ConfigReader.PieceSize) {
							resourceData = Arrays.copyOfRange(peerProcess.resourcePayload, startIndex, ConfigReader.fileSize);
						}
					
						else {
							resourceData = Arrays.copyOfRange(peerProcess.resourcePayload, startIndex, startIndex + ConfigReader.PieceSize);
						}
					
						if (!chokedPeer) {
							sendMessageToPeer(pieceMessage(pieceIndex, resourceData));
						}
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println(e.toString());
					}

					
				}
				else if(messageType.equals(MessageTypes.piece)) {
					
					byte[] pieceIndexFromRequest = new byte[4];
					inputStream.read(pieceIndexFromRequest);

					int pieceIndex = byteToInt(pieceIndexFromRequest);

					int messageLength = byteToInt(msgLength);

					byte[] payload = readMsgPayload(inputStream, messageLength - 5);

					peerProcess.bitField[pieceIndex / 8] |= 1 << (7 - (pieceIndex % 8));

					int start = pieceIndex * ConfigReader.PieceSize;
					for (int i = 0; i < payload.length; i++) {
						peerProcess.resourcePayload[start + i] = payload[i];
					}
					receivedPiecesCount++;
					peerProcess.logger.info("Peer: " + peerProcess.peerProcessID + " has downloaded the piece " + pieceIndex + " from Peer: "
							+ peerID + ". Now the number of pieces it has is : " + receivedPiecesCount);

					totTime += System.nanoTime() - procTime;
					dwldRate = (float) ((receivedPiecesCount * ConfigReader.PieceSize) / totTime);

					sendHaveMessageToAll(pieceIndexFromRequest);

					pieceIndex = requestedPIndex(peerProcess.bitField, peerBitField, peerProcess.requestedPiecesFromPeers);

					if (pieceIndex >= 0) {
						requestedIndex = pieceIndex;
						peerProcess.requestedPiecesFromPeers[pieceIndex].set(true);
						sendMessageToPeer(requestMessage(pieceIndex));
						procTime = System.currentTimeMillis();
					} else {
						checkAndSendNotInterested();
					}
				}
			}
		}
		catch (IOException ioException) {
			ioException.printStackTrace();
		}

	}

	public void sendMessageToPeer(byte[] msg) {
		try {
			outputStream.write(msg);
			outputStream.flush();
		} catch (IOException ex) {
			ex.printStackTrace();
		}

	}

	public void initiateClient(String clientId) {
		this.peerID = clientId;
		
		HandshakeMessage handshakeMessage = new HandshakeMessage(String.valueOf(peerProcess.peerProcessID));
		sendMessageToPeer(handshakeMessage.constructHandshakeMessage());
		readHandShakeMessage(inputStream);
		peerProcess.logger.info("Peer "+ peerProcess.peerProcessID + "makes a connection to Peer:" + peerID);
	}

	public void initiateServer() {
		this.peerID = readHandShakeMessage(inputStream);
		HandshakeMessage hsm = new HandshakeMessage(String.valueOf(peerProcess.peerProcessID));
		sendMessageToPeer(hsm.constructHandshakeMessage());
		peerProcess.logger.info("Peer: "+ peerProcess.peerProcessID + "makes a connection to Peer: " + peerID);
	}

	public void openComm() {
		peerProcess.logger.info("Peer: "+ peerProcess.peerProcessID + "is connected from Peer: " + peerID);
		sendMessageToPeer(bitFieldMessage(peerProcess.bitField));
		byte[] clientBitField = new byte[0];
		try {
			byte[] messageLength = new byte[4];
			inputStream.read(messageLength);
			clientBitField = readBitfieldPld(inputStream, byteToInt(messageLength) - 1);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		peerBitField = clientBitField;

		if (sendInterested(peerProcess.bitField, peerBitField)) {
			sendMessageToPeer(interestedMessage());
		} else {
			sendMessageToPeer(notInterestedMessage());
		}
	}

	public void closeSocket() {

		try {
			if (requestSocket!=null && !requestSocket.isClosed())
				requestSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void createAndWriteToFile() throws IOException {
		new File("peer_" + peerProcess.peerProcessID).mkdir();
		File file = new File("peer_" + peerProcess.peerProcessID + "/" + ConfigReader.fileName);
		FileOutputStream fileOutputStream = new FileOutputStream(file);
		fileOutputStream.write(peerProcess.resourcePayload);
		fileOutputStream.close();
		peerProcess.logger.info("Peer " + peerProcess.peerProcessID + " has downloaded the complete file.");
	}

	public void sendHaveMessageToAll(byte[] pieceIndex) {
		for (PeerClient peerClient : peerProcess.peerClientList) {
			peerClient.sendMessageToPeer(peerClient.haveMessage(pieceIndex));
		}
	}

	public void checkAndSendNotInterested() throws IOException {
		sendMessageToPeer(notInterestedMessage());
		if (Arrays.equals(peerProcess.bitField, peerProcess.torrentFile)) {
			for (PeerClient peerClient : peerProcess.peerClientList) {
				peerClient.sendMessageToPeer(peerClient.notInterestedMessage());
			}
			createAndWriteToFile();
		}
	}
	

	public int requestedPIndex(byte[] peerProcessBitfield, byte[] peerClientBitField,
			AtomicBoolean[] neededBitfield) {
		byte[] need = new byte[peerProcessBitfield.length];
		byte[] temp = new byte[peerProcessBitfield.length];
		Arrays.fill(temp, (byte)0);
		for (int ind = 0; ind < neededBitfield.length; ind++) {
			if (neededBitfield[ind].get()) {
				temp[ind / 8] |= 1 << (7 - (ind % 8));
			}
		}
		byte[] requestedBitFieldByte = temp;
		byte[] available = new byte[peerProcessBitfield.length];
		List<Integer> list = new ArrayList<Integer>();
		int i = 0;
		while (i < peerProcessBitfield.length) {
			available[i] = (byte) (peerProcessBitfield[i] & requestedBitFieldByte[i]);
			need[i] = (byte) ((available[i] ^ peerClientBitField[i]) & ~available[i]);

			if (need[i] != 0)
				list.add(i);
			i++;
		}
		if(list.isEmpty())
			return -1;
		int byteInd = list.get(new Random().nextInt(list.size()));
		byte rand = need[byteInd];
		int bitInd = getRndmSetBit(rand);				
		return (byteInd*8) + (7-bitInd);
	}
		
	public byte[] readMsgPayload(InputStream inputStream, int payloadLength) {
		byte[] result = new byte[0];
		try {
			while (payloadLength != 0) {
				int bytesAvailable = inputStream.available();
				int read = 0;
				if (payloadLength > bytesAvailable) {
					read = bytesAvailable;
				} else {
					read = payloadLength;
				}

				byte[] r = new byte[read];
				if (read != 0) {
					inputStream.read(r);
					result = mergeByte(result, r);
					payloadLength = payloadLength - read;
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return result;
	}
	
	public String readHandShakeMessage(InputStream in) {
		try {
			byte[] inputHeader = new byte[18];
			in.read(inputHeader);
			if (!(new String(inputHeader).equals("P2PFILESHARINGPROJ")))
				throw new RuntimeException("Header Mismatch");
			in.read(new byte[10]);
			byte[] peerId = new byte[4];
			in.read(peerId);
			return new String(peerId);
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
		return "";
	}
	
	public boolean sendInterested(byte[] peerProcessBitField, byte[] peerClientBitField) {
		byte isByteSet;
		int startInd = 0;
		while (startInd < peerProcessBitField.length) {
			isByteSet = (byte) (~peerProcessBitField[startInd] & peerClientBitField[startInd]);
			if (isByteSet != 0) {
				return true;
			}
			startInd++;
		}
		return false;
	}
	
	public int getRndmSetBit(byte msg) {
		int bitInd = new Random().nextInt(8);
		int i = 0;
		while (i < 8) {
			if ((msg & (1 << i)) != 0) {
				bitInd = i;
				break;
			}
			i++;
		}
		return bitInd;
	}
	
	public byte[] readBitfieldPld(InputStream ins,int length) throws IOException {
		
		byte[] clientBitField = new byte[length];
		byte[] type = new byte[1];			
		ins.read(type);
		byte val = intToByte(MessageTypes.bitfield.ordinal())[3];
		if(type[0] == val) 
		{				
			ins.read(clientBitField);				
		}
		return clientBitField;
	}

}
