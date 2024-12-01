// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) deadcode fieldsfirst 
// Source File Name:   PDNgame.java

package gz.common.logic.boards.draughts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import gz.common.logic.boards.BoardMove;
import gz.common.logic.boards.BoardPosition;

public class PDNgame {

	private static final String ToStr[][] = { { "", "5", "", "13", "", "21", "", "29" }, { "1", "", "9", "", "17", "", "25", "" }, { "", "6", "", "14", "", "22", "", "30" },
			{ "2", "", "10", "", "18", "", "26", "" }, { "", "7", "", "15", "", "23", "", "31" }, { "3", "", "11", "", "19", "", "27", "" }, { "", "8", "", "16", "", "24", "", "32" },
			{ "4", "", "12", "", "20", "", "28", "" }, };

	private int n;

	private String move;
	private boolean turn;
	private String event;
	private String black;
	private String white;
	private String date;
	private String result;
	private String game;

	public PDNgame() {
		reset();
	}

	public void addMove(BoardMove move) {
		if (!turn) {
			this.move = boardMoveToPDNMove(move);
			turn = true;
		} else {
			this.move += " " + boardMoveToPDNMove(move);
			concatToGame();
			n++;
			turn = false;
		}
	}

	private String boardMoveToPDNMove(BoardMove move) {
		BoardPosition lastPosition = move.get(0);
		String result = positionToStr(lastPosition);
		for (int i = 1; i < move.count(); i++) {
			BoardPosition sq = move.get(i);
			if (sq.getRow() - lastPosition.getRow() == 2 || sq.getCol() - lastPosition.getCol() == 2)
				result += "x" + positionToStr(sq);
			else
				result += "-" + positionToStr(sq);
		}
		return result;
	}

	private void concatToGame() {
		if (game.equals(""))
			game = n + ". " + move;
		else
			game += " " + n + ". " + move;
	}

	public String getBlack() {
		return black;
	}

	public String getDate() {
		return date;
	}

	public String getEvent() {
		return event;
	}

	public String getGame() {
		return game;
	}

	public String getResult() {
		return result;
	}

	public String getWhite() {
		return white;
	}

	private String positionToStr(BoardPosition square) {
		return ToStr[square.getCol()][square.getRow()];
	}

	public void reset() {
		n = 1;
		turn = false;
		event = "*";
		black = "*";
		white = "*";
		date = "*";
		result = "*";
		move = "";
		game = "";
	}

	public void saveToFile(File file) {
		PrintStream ps = null;
		try {
			ps = new PrintStream(file);

			ps.println("[Event \"" + event + "\"]");
			ps.println("[Date \"" + date + "\"]");
			ps.println("[White \"" + white + "\"]");
			ps.println("[Black \"" + black + "\"]");
			ps.println("[Result \"" + result + "\"]");

			if (turn) {
				move += " *";
				concatToGame();
			}
			ps.println(game);
			ps.println();
			ps.println();

			ps.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			if (ps != null)
				ps.close();

		}
	}

	public void saveToFile(String fileName) {
		saveToFile(new File(fileName));
	}

	public void setBlack(String s) {
		black = s;
	}

	public void setDate(String s) {
		date = s;
	}

	public void setEvent(String s) {
		event = s;
	}

	public void setResult(String s) {
		result = s;
	}

	public void setWhite(String s) {
		white = s;
	}

	@Override
	public String toString() {
		return "PDNgame [n=" + n + ", move=" + move + ", turn=" + turn + ", event=" + event + ", black=" + black + ", white=" + white + ", date=" + date + ", result=" + result + ", game=" + game
				+ "]";
	}
}
