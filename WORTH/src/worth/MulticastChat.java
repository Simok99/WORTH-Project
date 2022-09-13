package worth;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class MulticastChat {
	
	private MulticastSocket multicastSocket;	//Utilizzata per salvare messaggi nella struttura dati chat
	
	private String address;
	
	private int chatPort;
	
	public ArrayList<String> chat;
	
	public MulticastChat (int chatPort, int n1, int n2, int n3, int n4) {
		
		this.address = String.valueOf(n1)+"."+String.valueOf(n2)+"."+String.valueOf(n3)+"."+String.valueOf(n4);
		
		this.chatPort = chatPort;
		
		this.chat = new ArrayList<String>();
		
		startMulticast();
	}
	
	public void startMulticast() {
		
		try {
			this.multicastSocket = new MulticastSocket(this.chatPort);
		} catch (IOException e) {
			Thread.currentThread().interrupt();
			return;
		}
		
		InetAddress address = null;
		try {
			address = InetAddress.getByName(this.address);
		} catch (UnknownHostException e) {
			Thread.currentThread().interrupt();
			return;
		}
		
		try {
			this.multicastSocket.joinGroup(address);
		} catch (IOException e) {
			e.printStackTrace();
			Thread.currentThread().interrupt();
			return;
		}
		
	}
	
	public void saveChatMessage() {
		
		byte[] data;
		try {
			data = new byte[this.multicastSocket.getReceiveBufferSize()];
		} catch (SocketException e1) {
			e1.printStackTrace();
			return;
		}	//Crea l'array di byte con il contenuto da ricevere
		
		DatagramPacket packet = new DatagramPacket(data, data.length);	//Crea il pacchetto da ricevere tramite multicast
		try {
			this.multicastSocket.receive(packet);	//Ricerca un nuovo messaggio
		} catch(SocketTimeoutException e) {
			e.printStackTrace();
			return;
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
			
		String message = new String(data);
		
		this.chat.add(message);	//Salva il nuovo messaggio nella chat
	}
	
	public String getAddress() {
		return this.address+":"+this.chatPort;
	}

}
