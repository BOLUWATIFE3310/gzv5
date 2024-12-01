package gz.server.net.boards.chess;

import gz.server.net.Connection;
import gz.server.net.Lobby;
import gz.server.net.LobbyGame;
import gz.server.net.boards.BoardContainer;

public class ChessContainer extends BoardContainer {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5792149001016440764L;
	private static final String VERSION = "1.0";
	private static final String LAST_RELEASE = "07/11/2016 11:40";

	@Override
	protected Class<? extends Connection> getConnectionClass() {
		return ChessConnection.class;
	}

	@Override
	public String getGameName() {
		return "chess";
	}

	@Override
	public String getLastRelease() {
		return LAST_RELEASE;
	}

	@Override
	protected Class<? extends Lobby> getLobbyClass() {
		return ChessLobby.class;
	}

	@Override
	protected Class<? extends LobbyGame> getLobbyGameClass() {
		return ChessLobbyGame.class;
	}

	@Override
	public String getVersion() {
		return VERSION;
	}

}
