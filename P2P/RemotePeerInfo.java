
public class RemotePeerInfo {
	public String peerId;
	public String peerAddress;
	public String peerPort;
	public String isFilePresent;

	public RemotePeerInfo(String pId, String pAddress, String pPort, String fileExists) {
		peerId = pId;
		peerAddress = pAddress;
		peerPort = pPort;
		isFilePresent = fileExists;
	}
}
