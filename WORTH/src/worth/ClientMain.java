package worth;

public class ClientMain {

	public static void main(String[] args) {

		User u = new User();
		
		new Thread(u).start();
	}

}
