package gz.server.net.boards;

import java.util.Arrays;

import gz.server.net.Lobby;
import gz.server.net.LobbyGame;

public abstract class BoardLobby extends Lobby {

	protected BoardLobby() {
		super();
	}

	@Override
	protected boolean validateOptions(int[] options) {
		if (options[0] != 0 && Arrays.binarySearch(LobbyGame.MATCH_TIMES, options[0]) == -1)
			return false;
		
		if (options[1] != 0 && Arrays.binarySearch(LobbyGame.TIMES_PER_MOVE, options[1]) == -1)
			return false;
		
		if (options[0] == 0 && options[1] == 0)
			return false;
		
		return true;
	}

}
