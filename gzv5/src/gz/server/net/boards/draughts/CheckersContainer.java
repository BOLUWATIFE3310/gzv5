package gz.server.net.boards.draughts;

import gz.server.net.Connection;
import gz.server.net.Lobby;
import gz.server.net.LobbyGame;
import gz.server.net.boards.BoardContainer;

public class CheckersContainer extends BoardContainer {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5792149001016440764L;
	private static final String VERSION = "1.0";
	private static final String LAST_RELEASE = "13/09/2016 11:22";

	@Override
	protected Class<? extends Connection> getConnectionClass() {
		return DraughtsConnection.class;
	}

	@Override
	public String getGameName() {
		return "checkers";
	}

	@Override
	public String getLastRelease() {
		return LAST_RELEASE;
	}

	@Override
	protected Class<? extends Lobby> getLobbyClass() {
		return CheckersLobby.class;
	}

	@Override
	protected Class<? extends LobbyGame> getLobbyGameClass() {
		return CheckersLobbyGame.class;
	}

	@Override
	public String getVersion() {
		return VERSION;
	}

}
