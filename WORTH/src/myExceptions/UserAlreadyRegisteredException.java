package myExceptions;
/**
 * Eccezione che indica che il nome utente che si sta registrando � gi�
 * stato registrato in precedenza
 */
public class UserAlreadyRegisteredException extends Exception {

	private static final long serialVersionUID = 1L;

	public UserAlreadyRegisteredException() {
		super("Nome utente gi� registrato");
	}
}
