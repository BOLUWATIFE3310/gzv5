package gz.server.net.billiards;

import gz.server.net.Connection;
import gz.server.net.Container;
import gz.server.net.Lobby;
import gz.server.net.LobbyGame;

public class BilliardsContainer extends Container {

	/**
	 * 
	 */
	private static final long serialVersionUID = 138930677215995954L;

	private static final String VERSION = "1.0";
	private static final String LAST_RELEASE = "03/10/2016 06:20";

	public BilliardsContainer() {
		super();
	}

	@Override
	protected Class<? extends Connection> getConnectionClass() {
		return BilliardsConnection.class;
	}

	@Override
	public String getGameName() {
		return "billiards";
	}

	@Override
	public String getLastRelease() {
		return LAST_RELEASE;
	}

	@Override
	protected Class<? extends Lobby> getLobbyClass() {
		return BilliardsLobby.class;
	}

	@Override
	protected Class<? extends LobbyGame> getLobbyGameClass() {
		return BilliardsLobbyGame.class;
	}

	@Override
	public String getVersion() {
		return VERSION;
	}

}
