package myExceptions;
/**
 * Eccezione che indica che il nome utente che si sta registrando è già
 * stato registrato in precedenza
 */
public class UserAlreadyRegisteredException extends Exception {

	private static final long serialVersionUID = 1L;

	public UserAlreadyRegisteredException() {
		super("Nome utente già registrato");
	}
}
