
import java.io.BufferedReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;


public class peerProcess {

	static List<PeerClient> peerClientList = Collections.synchronizedList(new ArrayList<PeerClient>());
	static PeerClient optimalUnchokedNeighbor;
	static byte[] bitField, resourcePayload, torrentFile;
	static AtomicBoolean[] requestedPiecesFromPeers;
	static Logger logger;

	ScheduledExecutorService taskScheduler = Executors.newScheduledThreadPool(3);

	Integer port = 8000;
	static Integer peerProcessID;
	static ServerSocket serverSocket;

	public static void main(String[] args) throws Exception {

		peerProcess peerProcess = new peerProcess();
		peerProcessID = Integer.parseInt(args[0]);

		ConfigReader configReader = new ConfigReader();
		logger = ProcessLogger.getLogger(peerProcessID);

		List<RemotePeerInfo> connectedPeers = new ArrayList<RemotePeerInfo>();
		List<RemotePeerInfo> peersToJoin = new ArrayList<RemotePeerInfo>();

		boolean isFileAvailable = false;
		for (RemotePeerInfo remotePeerInfo : ConfigReader.peerInfoList) {
			if (Integer.parseInt(remotePeerInfo.peerId) < peerProcessID) {
				connectedPeers.add(remotePeerInfo);
			} else if (Integer.parseInt(remotePeerInfo.peerId) == peerProcessID) {
				peerProcess.port = Integer.parseInt(remotePeerInfo.peerPort);
				if (remotePeerInfo.isFilePresent.equals("1"))
					isFileAvailable = true;
			} else {
				peersToJoin.add(remotePeerInfo);
			}
		}
		initializePeerProcess(peerProcess, configReader, connectedPeers, peersToJoin,
				isFileAvailable);
	}

	private static void initializePeerProcess(peerProcess peerProcessObj, ConfigReader configObj,
			List<RemotePeerInfo> connectionEstablishedPeers, List<RemotePeerInfo> yetToConnectPeers,
			boolean isFileAvailable) throws IOException {
		bitField = new byte[ConfigReader.noOfBytes];
		requestedPiecesFromPeers = new AtomicBoolean[ConfigReader.noOfPieces];
		Arrays.fill(requestedPiecesFromPeers, new AtomicBoolean(false));
		resourcePayload = new byte[ConfigReader.fileSize];
		torrentFile = new byte[ConfigReader.noOfBytes];
		initializeBitFieldAndResource(isFileAvailable, ConfigReader.noOfPieces);
		listenToConnectedPeers(connectionEstablishedPeers, configObj);
		serverSocket = new ServerSocket(peerProcessObj.port);
		logger.info("Socket Opened on port: " + peerProcessObj.port);
		listenToPeers(yetToConnectPeers, configObj);
		determineOptmisticallyUnchokedNeighbour();
		intitiateTaskSchedulers(peerProcessObj);
	}

	public static void readTorrentFile() throws IOException {
		try {
			File resource = new File("peer_" + peerProcess.peerProcessID + "/" + ConfigReader.fileName);
			FileInputStream filePayload = new FileInputStream(resource);
			filePayload.read(resourcePayload);
			filePayload.close();
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		}
	}

	public static void initializeBitFieldAndResource(boolean fileAvailable, int pieces) throws IOException {
		Arrays.fill(torrentFile, (byte) 255);
		if (fileAvailable) {
			readTorrentFile();
			Arrays.fill(bitField, (byte) 255);
			if (pieces % 8 != 0) {
				int end = (int) pieces % 8;
				bitField[bitField.length - 1] = 0;
				torrentFile[bitField.length - 1] = 0;
				while (end != 0) {
					bitField[bitField.length - 1] |= (1 << (8 - end));
					torrentFile[bitField.length - 1] |= (1 << (8 - end));
					end--;
				}
			}
		} else {
			if (pieces % 8 != 0) {
				int end = (int) pieces % 8;
				torrentFile[bitField.length - 1] = 0;
				while (end != 0) {
					torrentFile[bitField.length - 1] |= (1 << (8 - end));
					end--;
				}
			}
		}
	}

	public static void listenToConnectedPeers(List<RemotePeerInfo> connectedPeers, ConfigReader configReader) {

		for (RemotePeerInfo peerInfo : connectedPeers) {
			try {
				PeerClient peerClient = new PeerClient(new Socket(peerInfo.peerAddress, Integer.parseInt(peerInfo.peerPort)),
						true, peerInfo.peerId, configReader);

				peerClient.start();
				peerClientList.add(peerClient);
				logger.info("Peer " + peerProcessID + " makes a connection to Peer " + peerInfo.peerId + ".");
			} catch (Exception ex) {
				ex.printStackTrace();
				logger.info(ex.toString());
			}

		}

	}

	public static void determineOptmisticallyUnchokedNeighbour() {
		List<PeerClient> interestedAndChokedNeighbour = new ArrayList<PeerClient>();

		for (PeerClient peerClient : peerClientList) {
			if (peerClient.interestedPeer && peerClient.chokedPeer) {
				interestedAndChokedNeighbour.add(peerClient);
			}
		}

		if (interestedAndChokedNeighbour.isEmpty()) {
			optimalUnchokedNeighbor = null;
		} else {
			optimalUnchokedNeighbor = interestedAndChokedNeighbour
					.get(new Random().nextInt(interestedAndChokedNeighbour.size()));
		}
	}

	public static void listenToPeers(List<RemotePeerInfo> yetToConnectPeers, ConfigReader configObj) {
		try {
			for (RemotePeerInfo remotePeerInfoObj : yetToConnectPeers) {
				Runnable peerConnection = new Runnable() {
					
				@Override
			     public void run() {
					try {
						PeerClient futurePeer = new PeerClient(serverSocket.accept(), false, remotePeerInfoObj.peerId,
								configObj);
						logger.info(
								"Peer " + peerProcessID + " is connected from Peer " + remotePeerInfoObj.peerId + ".");
						peerClientList.add(futurePeer);
						futurePeer.start();
					} catch (IOException e) {
						logger.info(e.getMessage());
					}
				}
				};
				new Thread(peerConnection).start();
			}
		} catch (Exception ex) {
			logger.info("Exception while listening to future peers :" + ex.getMessage());
			ex.printStackTrace();
		}
	}

	public void startPrefNeighbSched(int k, int p) {
		Runnable findPreferredNeibhbours = new Runnable() {
			
		@Override
	     public void run() { 
			redeterminePreferredNeighbours(k);
		}
		};
		taskScheduler.scheduleAtFixedRate(findPreferredNeibhbours, p, p, TimeUnit.SECONDS);
	}

	public void redeterminePreferredNeighbours(int noOfPreferreNeighbours) {
		try {
			Collections.sort(peerClientList, new Comparator<PeerClient>(){
				  public int compare(PeerClient client1, PeerClient client2){
				    return client2.dwldRate.compareTo(client1.dwldRate);
				  }
				});
			int counter = 0;
			List<String> prefferredList = new ArrayList<String>();

			for (PeerClient client : peerClientList) {
				if (client.interestedPeer) {
					if (counter < noOfPreferreNeighbours) {
						if (client.chokedPeer) {
							client.chokedPeer = false;
							client.sendMessageToPeer(client.unChokeMessage());
						}
						prefferredList.add(client.peerID);
					} else {

						if (!client.chokedPeer && client != optimalUnchokedNeighbor) {
							client.chokedPeer = true;
							client.sendMessageToPeer(client.chokeMessage());
						}
					}

					counter++;
				}
			}
			logger.info("Peer " + peerProcessID + " with preferred neighbours:" + prefferredList);
		} catch (Exception e) {
			logger.info(e.toString());
		}
	}

	public void startOptiNeighbSched(int m) {

		Runnable getOptimisticallyPreferreNeighbour = new Runnable() {
			
		@Override
	     public void run() { 
			reDetermineOptimisticallyPreferredNeighbour();
		}
		};
		taskScheduler.scheduleAtFixedRate(getOptimisticallyPreferreNeighbour, m, m, TimeUnit.SECONDS);
	}

	public void reDetermineOptimisticallyPreferredNeighbour() {
		try {

			List<PeerClient> interestedAndChokedNeighbour = new ArrayList<PeerClient>();

			for (PeerClient peerClient : peerClientList) {
				if (peerClient.interestedPeer && peerClient.chokedPeer) {
					interestedAndChokedNeighbour.add(peerClient);
				}
			}

			unchokeOptimalNeighbor(interestedAndChokedNeighbour);

			if (optimalUnchokedNeighbor != null)
				logger.info("Peer: " + peerProcessID + " has the optimistically unchoked neighbor Peer: "
						+ optimalUnchokedNeighbor.peerID);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.toString());
		}
	}

	private void unchokeOptimalNeighbor(List<PeerClient> interestedAndChokedNeighbour) {
		if (!interestedAndChokedNeighbour.isEmpty()) {
			if (optimalUnchokedNeighbor != null) {
				optimalUnchokedNeighbor.chokedPeer = true;
				optimalUnchokedNeighbor.sendMessageToPeer(optimalUnchokedNeighbor.chokeMessage());
			}
			optimalUnchokedNeighbor = interestedAndChokedNeighbour
					.get(new Random().nextInt(interestedAndChokedNeighbour.size()));
			optimalUnchokedNeighbor.chokedPeer = false;
			optimalUnchokedNeighbor.sendMessageToPeer(optimalUnchokedNeighbor.unChokeMessage());

		} else {
			if (optimalUnchokedNeighbor != null) {
				if (!optimalUnchokedNeighbor.chokedPeer) {
					optimalUnchokedNeighbor.chokedPeer = true;
					optimalUnchokedNeighbor.sendMessageToPeer(optimalUnchokedNeighbor.chokeMessage());
				}
				optimalUnchokedNeighbor = null;
			}
		}
	}

	public void checkIfFileTransferIsComplete() {
		Runnable pollForPeerFile = new Runnable() {
			
		@Override
	     public void run() {
			checkAndCloseSocket(hasAllClientsReceived());
		}
		};
		taskScheduler.scheduleAtFixedRate(pollForPeerFile, 5, 3, TimeUnit.SECONDS);
	}

	public boolean hasAllClientsReceived() {
		boolean isFileReceived = true;
		for (PeerClient ct : peerClientList) {
			if (!Arrays.equals(ct.peerBitField, torrentFile)) {
				logger.info("Peer " + ct.peerID + " yet to receive the full file.");
				isFileReceived = false;
				break;
			}
		}
		return isFileReceived;
	}

	public void checkAndCloseSocket(boolean isTransferred) {
		logger.info("Complete File Status: " + isTransferred);
		if (isTransferred && Arrays.equals(bitField, torrentFile)) {
			for (PeerClient peerClient : peerClientList) {
				peerClient.killProcess.set(true);
				if (peerClient.killProcess.get()) {
					peerProcess.logger.info("Closing Socket");
					peerClient.closeSocket();
				}
			}
			taskScheduler.shutdown();
			try {
				if (!serverSocket.isClosed())
					serverSocket.close();
			} catch (IOException e) {
				//e.printStackTrace();
				logger.info("Exception During socket closing");
			} finally {
				logger.info("ShuttingDown the PeerProcess with Id: " + peerProcessID);
				System.exit(0);
			}
		}
	}

	public static void intitiateTaskSchedulers(peerProcess peerProcess) {
		peerProcess.startPrefNeighbSched(ConfigReader.noOfPreferredNeighbors, ConfigReader.unChokingInterval);
		peerProcess.startOptiNeighbSched(ConfigReader.optimisticUnchokingInterval);
		peerProcess.checkIfFileTransferIsComplete();
	}
	public static class KeyPair {
        String key;
        String value;
        KeyPair(String key, String value)
        {   this.key = key;
            this.value = value;
        }
    }

    public static class Output {
        int score;
        String[] seqIds = new String[2];
        int[] startPosition = new int[2];
        String[] outputSeq = new String[2];
    }

    public static void preprocessBuffers(int[] charKeys, int algo, int kVal, int gapPenalty, String[] args){

        List<KeyPair> inputQuery = new ArrayList<>();
        List<KeyPair> databaseSet = new ArrayList<>();
        String charSeq = "";

        try {
            BufferedReader queryReader = new BufferedReader(new FileReader(args[1]));
            BufferedReader databaseReader = new BufferedReader(new FileReader(args[2]));
            BufferedReader alphabetReader = new BufferedReader(new FileReader(args[3]));
            BufferedReader scoreReader = new BufferedReader(new FileReader(args[4]));

            inputQuery = parse(queryReader, inputQuery);
            databaseSet = parse(databaseReader,databaseSet);

            charSeq = charAlign(alphabetReader,charKeys);
            int[][] scoringMatrix = new int[charSeq.length()][charSeq.length()];

            String line = "";
            int r = 0;
            try {
                line = scoreReader.readLine();
                while ( line != null ) {
                    String[] score = line.trim().split("\\s+");
                    for(int j = 0; j < score.length; j++)
                    {
                        scoringMatrix[r][j] = Integer.parseInt(score[j]);
                    }
                    r++;
                    line = scoreReader.readLine();
                }
            }
            catch (Exception error)
            {
                error.printStackTrace();
            }


            queryReader.close();
            databaseReader.close();
            alphabetReader.close();
            scoreReader.close();
        }
        catch (IOException error) {
            error.printStackTrace();
        }
    }
    public  static List<KeyPair> parse(BufferedReader bufferedReader, List<KeyPair> inputData) throws IOException {
        List<String> seqIds = new ArrayList<String>();
        List<String> seqList = new ArrayList<String>();
        StringBuilder seqString = new StringBuilder();
        String line = "";
        int len = 5;
        try{
            line = bufferedReader.readLine();
            while ( line != null ) {
                if(line.length() > len)
                {
                    if (line.substring(0, len).equals(">hsa:")) {
                        if (seqString.length() != 0) {
                            seqList.add(seqString.toString());
                        }
                        seqString = new StringBuilder();
                        String seqId = line.substring(len, line.indexOf(" "));
                        seqIds.add(seqId);
                    }
                    else
                        seqString.append(line);
                }
                else {
                    seqString.append(line);
                }
                line = bufferedReader.readLine();
            }
            if(seqString.length() != 0) {
                seqList.add(seqString.toString());
            }
        }
        catch (Exception error){
            System.out.println(error);
        }
        int l = 0;
        while(l<seqList.size()){
            KeyPair p = new KeyPair(seqIds.get(l), seqList.get(l));
            inputData.add(p);
            l++;
        }
        return inputData;
    }

    public static String charAlign(BufferedReader alphabet, int[] charSeq) {
        String line = "";
        StringBuilder charString = new StringBuilder();
        for (int i=0;i<256;i++) {
            charSeq[i] = -1;
        }
        try {
            line = alphabet.readLine();
            while ( line != null ) {
                charString.append(line);
                int i = 0;
                while( i<line.length() ){
                    charSeq[line.charAt(i)+32] = i;
                    charSeq[line.charAt(i)] = i;
                    charSeq[line.charAt(i)-32] = i;
                    i++;
                }
                line = alphabet.readLine();
            }
        }
        catch (Exception error)
        {
            error.printStackTrace();
        }
        return charString.toString();
    }


}