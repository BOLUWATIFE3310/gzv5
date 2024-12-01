package gz.server.net.boards.draughts;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import gz.common.logic.Game.StopReason;
import gz.common.logic.MultiplayerGame;
import gz.common.logic.Player;
import gz.common.logic.boards.BoardMove;
import gz.common.logic.boards.BoardPosition;
import gz.common.logic.boards.draughts.DraughtsConfig;
import gz.common.logic.boards.draughts.DraughtsGame;
import gz.common.logic.boards.draughts.DraughtsGameController;
import gz.common.logic.boards.draughts.DraughtsPiece;
import gz.common.logic.boards.draughts.DraughtsPlayer;
import gz.common.logic.boards.draughts.PDNgame;
import gz.server.net.Connection;
import gz.server.net.Container;
import gz.server.net.Lobby;
import gz.server.net.boards.BoardLobbyGame;
import gz.shared.DBV5Consts;
import gz.util.GZStruct;
import gz.util.GZUtils;

public class CheckersLobbyGame extends BoardLobbyGame {

	protected class DraughtsController extends BoardController implements DraughtsGameController {

	}

	public class DraughtsSeat extends BoardSeat {

		protected DraughtsSeat(int index, Connection user) {
			super(index, user);
		}

	}

	private DraughtsGame game;

	private PDNgame pdn;

	private boolean restoringGame = false;

	public CheckersLobbyGame() {
		super();
	}

	@Override
	protected void afterClose() {
		super.afterClose();

		game = null;
	}

	@Override
	protected MultiplayerGame createGame() {
		game = new DraughtsGame(new DraughtsController());
		switch (variant) {
			case "gzcheckers":
				game.setConfig(DraughtsConfig.GAMEZERV5);
				break;
				
			case "uscheckers":
				game.setConfig(DraughtsConfig.AMERICAN);
				break;
				
			case "brdraughts":
				game.setConfig(DraughtsConfig.BRAZILIAN);
				break;
				
			case "spdraughts":
				game.setConfig(DraughtsConfig.SPANISH);
				break;
				
			case "rudraughts":
				game.setConfig(DraughtsConfig.RUSSIAN);
				break;
				
			case "itdraughts":
				game.setConfig(DraughtsConfig.ITALIAN);
				break;
		}

		return game;
	}

	@Override
	protected Player[] createPlayers() {
		return new DraughtsPlayer[getMaxSeatCount()];
	}

	@Override
	protected Seat createSeat(int seatIndex, Connection user) {
		return new DraughtsSeat(seatIndex, user);
	}

	@Override
	protected Seat[] createSeats() {
		return new DraughtsSeat[getMaxSeatCount()];
	}

	@Override
	public CheckersContainer getContainer() {
		return (CheckersContainer) super.getContainer();
	}

	@Override
	protected DraughtsGame getGame() {
		return game;
	}

	private String getPiece(DraughtsPiece piece) {
		if (piece.isBlackMan())
			return "b_c";

		if (piece.isBlackKing())
			return "b_k";

		if (piece.isWhiteMan())
			return "w_c";

		if (piece.isWhiteKing())
			return "w_k";

		return "";
	}

	@Override
	protected String getTurnStr(int turn) {
		if (turn == 0)
			return "w";

		if (turn == 1)
			return "b";

		return "n";
	}

	@Override
	protected void notifyGameState(Connection connection, GZStruct gd) {
		gd.setString("v", variant);
		
		int seatIndex = getSeatIndex(connection);
		if (seatIndex == -1 || restoringGame) {
			DraughtsGame game = getGame();
			String brds = null;
			for (int row = 0; row < game.getRowCount(); row++)
				for (int col = 0; col < game.getColCount(); col++) {
					if (!game.isValidPos(row, col))
						continue;
	
					DraughtsPiece piece = (DraughtsPiece) game.getBoard(row, col);
					if (piece == null)
						continue;
	
					String s = "_" + Integer.toString(row + 1) + Integer.toString(col + 1) + "_|" + getPiece(piece);
					if (brds == null)
						brds = s;
					else
						brds += ";" + s;
				}
	
			gd.setString("brds", brds == null ? "" : brds);
		}
	}

	@Override
	protected void onMove(BoardMove move) {
		super.onMove(move);

		ArrayList<BoardPosition> positions = new ArrayList<>();
		for (int i = 0; i < move.count(); i++) {
			BoardPosition position = move.get(i);
			positions.add(new BoardPosition(position.getRow(), 7 - position.getCol()));
		}

		pdn.addMove(new BoardMove(positions));
	}

	@Override
	protected void onStartGame() {
		super.onStartGame();

		pdn.reset();
		pdn.setEvent("Game " + getID() + " at " + getLobby().getName());

		if (seats[0].getConnection() == null)
			pdn.setWhite("Computer (COMPUTER)");
		else
			pdn.setWhite(seats[0].getConnection().getNick() + " (" + seats[1].getConnection().getGZID() + ")");

		if (seats[1].getConnection() == null)
			pdn.setBlack("Computer (COMPUTER)");
		else
			pdn.setBlack(seats[1].getConnection().getNick() + " (" + seats[0].getConnection().getGZID() + ")");

		pdn.setDate(GZUtils.dateToStr_(new Timestamp(System.currentTimeMillis())));
	}

	@Override
	protected void onStopGame(StopReason reason) {
		super.onStopGame(reason);

		int winner = getWinner();
		pdn.setResult(winner == -1 ? "0-0" : winner == 0 ? "1-0" : "0-1");

		File file = new File(container.getHomeDir(), "games");
		if (!file.exists())
			if (!file.mkdirs()) {
				container.logToErr("Can't create the game directory '" + file + "'.");
				return;
			}

		file = new File(file, pdn.getDate() + ".pdn");
		pdn.saveToFile(file);
	}

	@Override
	protected void open(Container server, Lobby lobby, int number, String variant, boolean allowWatchers, int[] options, Connection... players) throws IOException {
		super.open(server, lobby, number, variant, allowWatchers, options, players);

		pdn = new PDNgame();
	}
	
	private int saveGame() {
		try {
			int currentTurn = game.getCurrentTurn();
			String state = game.toStateString();
			return container.executeTransaction((connection) -> {
				try (ResultSet rs = connection.insert(DBV5Consts.TABLE_SAVED_GAMES, true, new Object[] { null, null, currentTurn, state })) {
					if (!rs.next())
						return null;
					
					return rs.getInt(1);
				}
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}
		
		return -1;
	}
	
	private Object[] getSavedGameByID(int id) {
		try {
			return container.executeTransaction((connection) -> {
				try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_SAVED_GAMES, new String[] {"id"}, new Object[] {id})) {
					if (!rs.next())
						return null;
					
					Object[] result = new Object[3];
					result[0] = id;
					result[1] = rs.getInt("turn");
					result[2] = rs.getString("state");
					return result;
				}
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}
		
		return null;
	}
	
	private Object[] getLastSavedGame() {
		try {
			return container.executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery("SELECT * FROM " + DBV5Consts.TABLE_SAVED_GAMES + " ORDER BY saved_when DESC LIMIT 1")) {
					if (!rs.next())
						return null;
					
					Object[] result = new Object[3];
					result[0] = rs.getInt("id");
					result[1] = rs.getInt("turn");
					result[2] = rs.getString("state");
					return result;
				}
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}
		
		return null;		
	}
	
	private int restoreGame(Object[] r) {
		if (r == null)
			return -1;
		
		int turn = (int) r[1];
		String state = (String) r[2];
		game.setupPosition(state, turn);
		
		restoringGame  = true;
		try {
		for (Connection connection: connections)
			notifyGameSetup(connection);
		} finally {
			restoringGame = false;
		}
		
		return (int) r[0];
	}
	
	private void restoreGame(int id) {
		restoreGame(getSavedGameByID(id));
	}
	
	private int restoreLastGame() {
		return restoreGame(getLastSavedGame());
	}
	
	protected void parseSystemCommand(Connection connection, String command) {
		if (command.equals("save game")) {
			int id = saveGame();
			GZStruct response = new GZStruct();
			response.setString("alert", "Game saved with id " + id);
			connection.postBlock(response);
		} else if (command.startsWith("restore game ")) {
			command = command.substring("restore game ".length());
			int id;
			try {
				id = Integer.parseInt(command);
			} catch (NumberFormatException e) {
				GZStruct response = new GZStruct();
				response.setString("alert", "Game ID should be a number but the string '" + command + "' has found.");
				connection.postBlock(response);
				return;
			}
			
			restoreGame(id);
			
			GZStruct response = new GZStruct();
			response.setString("alert", "Game restored.");
			connection.postBlock(response);
		} else if (command.equals("restore last game")) {
			int id = restoreLastGame();
			
			GZStruct response = new GZStruct();
			response.setString("alert", "Game " + id + " restored.");
			connection.postBlock(response);
		} else
			super.parseSystemCommand(connection, command);
	}

}
