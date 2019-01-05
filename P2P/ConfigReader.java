
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class ConfigReader {
	static Integer noOfPreferredNeighbors;
	static Integer unChokingInterval;
	static Integer optimisticUnchokingInterval;
	static String fileName;
	static Integer fileSize;
	static Integer PieceSize;
	static Integer port = 8000;
	static ArrayList<RemotePeerInfo> peerInfoList;
	static Integer noOfPieces;
	static Integer noOfBytes;
	private BufferedReader br;

	public ConfigReader() throws IOException {
		peerInfoList = new ArrayList<RemotePeerInfo>();
		FileReader fileReader = new FileReader("Common.cfg");
		br = new BufferedReader(fileReader);
		String line1 = br.readLine();
		while (line1 != null) {
			String[] tokens = line1.split("\\s+");
			switch (tokens[0]) {
		
			case "NumberOfPreferredNeighbors":
				ConfigReader.noOfPreferredNeighbors = Integer.parseInt(tokens[1]);
				break;
		
			case "UnchokingInterval":
				ConfigReader.unChokingInterval = Integer.parseInt(tokens[1]);
				break;
		
			case "OptimisticUnchokingInterval":
				ConfigReader.optimisticUnchokingInterval = Integer.parseInt(tokens[1]);
				break;
		
			case "FileName":
				ConfigReader.fileName = tokens[1];
				break;
		
			case "FileSize":
				ConfigReader.fileSize = Integer.parseInt(tokens[1]);
				break;
		
			case "PieceSize":
				ConfigReader.PieceSize = Integer.parseInt(tokens[1]);
				break;
		
			}
			line1 = br.readLine();
		}
		FileReader fr = new FileReader("PeerInfo.cfg");
		br = new BufferedReader(fr);
		String line = br.readLine();
		while (line != null) {
			String[] values = line.split("\\s+");
			peerInfoList.add(new RemotePeerInfo(values[0], values[1], values[2], values[3]));
			line = br.readLine();
		}
		noOfPieces = (fileSize % PieceSize == 0) ? fileSize / PieceSize : (fileSize / PieceSize) + 1;
		noOfBytes = (noOfPieces % 8 == 0) ? noOfPieces / 8 : (noOfPieces / 8) + 1;
	}
}
