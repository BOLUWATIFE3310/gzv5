package gz.server.net.boards.chess;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;

import gz.common.logic.Game.StopReason;
import gz.common.logic.MultiplayerGame;
import gz.common.logic.Player;
import gz.common.logic.boards.BoardMove;
import gz.common.logic.boards.BoardPosition;
import gz.common.logic.boards.chess.ChessBishop;
import gz.common.logic.boards.chess.ChessGame;
import gz.common.logic.boards.chess.ChessGameController;
import gz.common.logic.boards.chess.ChessKing;
import gz.common.logic.boards.chess.ChessKnight;
import gz.common.logic.boards.chess.ChessPawn;
import gz.common.logic.boards.chess.ChessPiece;
import gz.common.logic.boards.chess.ChessQueen;
import gz.common.logic.boards.chess.ChessTower;
import gz.common.logic.boards.chess.ChessPlayer;
import gz.common.logic.boards.draughts.PDNgame;
import gz.server.net.Connection;
import gz.server.net.Container;
import gz.server.net.Lobby;
import gz.server.net.boards.BoardLobbyGame;
import gz.util.GZStruct;
import gz.util.GZUtils;

public class ChessLobbyGame extends BoardLobbyGame {

	protected class ChessController extends BoardController implements ChessGameController {

	}

	public class ChessSeat extends BoardSeat {

		protected ChessSeat(int index, Connection user) {
			super(index, user);
		}

	}

	private ChessGame game;

	private PDNgame pdn;

	public ChessLobbyGame() {
		super();
	}

	@Override
	protected void afterClose() {
		super.afterClose();

		game = null;
	}

	@Override
	protected MultiplayerGame createGame() {
		game = new ChessGame(new ChessController());
		return game;
	}

	@Override
	protected Player[] createPlayers() {
		return new ChessPlayer[getMaxSeatCount()];
	}

	@Override
	protected Seat createSeat(int seatIndex, Connection user) {
		return new ChessSeat(seatIndex, user);
	}

	@Override
	protected Seat[] createSeats() {
		return new ChessSeat[getMaxSeatCount()];
	}

	@Override
	public ChessContainer getContainer() {
		return (ChessContainer) super.getContainer();
	}

	@Override
	protected ChessGame getGame() {
		return game;
	}

	private String getPiece(ChessPiece piece) {
		if (piece instanceof ChessPawn)
			return piece.isBlack() ? "b_p" : "w_p";

		if (piece instanceof ChessBishop)
			return piece.isBlack() ? "b_b" : "w_b";

		if (piece instanceof ChessKnight)
			return piece.isBlack() ? "b_h" : "w_h";

		if (piece instanceof ChessTower)
			return piece.isBlack() ? "b_r" : "w_r";
		
		if (piece instanceof ChessQueen)
			return piece.isBlack() ? "b_q" : "w_q";
		
		if (piece instanceof ChessKing)
			return piece.isBlack() ? "b_k" : "w_k";

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
		int seatIndex = getSeatIndex(connection);
		if (seatIndex != -1)
			return;

		ChessGame game = getGame();
		String brds = null;
		for (int row = 0; row < game.getRowCount(); row++)
			for (int col = 0; col < game.getColCount(); col++) {
				if (!game.isValidPos(row, col))
					continue;

				ChessPiece piece = (ChessPiece) game.getBoard(row, col);
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

}
