package gz.common.logic.billiards;

import java.util.ArrayList;
import java.util.Arrays;

import common.util.RandomUtil;
import gz.common.logic.MultiplayerGame;
import gz.common.logic.Player;

public abstract class BilliardsGame extends MultiplayerGame {

	public class Ball {
		private int index;
		private int x;
		private int y;
		private boolean visible;
		private boolean modified;
		private boolean restored;

		public Ball(Ball other) {
			index = other.index;
			x = other.x;
			y = other.y;
			visible = other.visible;
			modified = other.modified;
			restored = false;
		}

		protected Ball(int index) {
			this.index = index;

			x = 0;
			y = 0;
			visible = true;
			modified = false;
			restored = false;
		}

		protected Ball(int index, Point2D position) {
			this.index = index;

			x = position.getX();
			y = position.getY();
			visible = true;
			modified = false;
			restored = false;
		}

		public int getIndex() {
			return index;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public boolean isModified() {
			return modified;
		}

		public boolean isVisible() {
			return visible;
		}

		public void reset() {
			Point2D position = initialPositions[index];
			if (isColliding(position, index))
				position = getRandomPositionAround(position, index);
				
			x = position.getX();
			y = position.getY();

			if (!visible)
				restored = true;

			visible = true;
			modified = true;
		}
		
		public void restoreAtRandomPosition() {
			Point2D position = getRandomPosition(index);
			x = position.getX();
			y = position.getY();

			if (!visible)
				restored = true;

			visible = true;
			modified = true;
		}

		public void setVisible(boolean value) {
			if (!value)
				restored = false;
			else if (visible && !value)
				restored = true;

			visible = value;
			modified = true;
		}

		@Override
		public String toString() {
			return "Ball [index=" + index + ", x=" + x + ", y=" + y + ", visible=" + visible + ", modified=" + modified + ", restored=" + restored + "]";
		}

		public boolean wasRestored() {
			return restored;
		}
	}

	public class Cue {
		private int cueBallIndex;
		private int x;
		private int y;
		private int angle;
		private boolean positionUpdated;

		protected Cue() {
			cueBallIndex = 0;
			Point2D p = initialPositions[0];
			x = p.getX();
			y = p.getY();
			angle = 0;
			positionUpdated = true;
		}

		protected Cue(Cue other) {
			cueBallIndex = other.cueBallIndex;
			x = other.x;
			y = other.y;
			angle = other.angle;
			positionUpdated = true;
		}

		public int getAngle() {
			return angle;
		}

		public int getCueBallIndex() {
			return cueBallIndex;
		}

		public Point2D getPosition() {
			positionUpdated = false;
			return new Point2D(x, y);
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public boolean isPositionUpdated() {
			return positionUpdated;
		}

		@Override
		public String toString() {
			return "Cue [cueBallIndex=" + cueBallIndex + ", x=" + x + ", y=" + y + ", angle=" + angle + ", positionUpdated=" + positionUpdated + "]";
		}
	}

	public class GameState {
		protected int turnNumber;
		protected int currentTurn;
		protected boolean selecting;
		protected Ball[] balls;
		protected Cue cue;
		protected int cueBallIndex;
		protected int hp;
		protected Shot shot;
		protected int[] ballIndices;
		protected Point2D[] ballPositions;
		protected int[] pottedBallIndices;
		protected int[] pottedBallSlots;
		protected int[] hitBallIndices;

		protected GameState() {
			turnNumber = 0;
			currentTurn = getInitialTurn();
			selecting = false;

			cue = new Cue();

			balls = new Ball[bnm];
			for (int i = 0; i < bnm; i++) {
				balls[i] = new Ball(i, initialPositions[i]);
				balls[i].visible = true;
				balls[i].modified = false;
			}

			hp = initialHandBallMode();

			shot = null;
			cueBallIndex = 0;
		}

		protected GameState(GameState lastState) {
			turnNumber = lastState.turnNumber + 1;
			currentTurn = lastState.currentTurn;

			cue = new Cue(lastState.cue);

			balls = new Ball[bnm];
			for (int i = 0; i < bnm; i++)
				balls[i] = new Ball(lastState.balls[i]);

			hp = lastState.hp;

			shot = null;
			cueBallIndex = lastState.cueBallIndex;
		}

		public Ball getBall(int index) {
			return balls[index];
		}

		public Cue getCue() {
			return cue;
		}

		public int getCueBallIndex() {
			return cueBallIndex;
		}

		public int getCurrentTurn() {
			return currentTurn;
		}

		public int getHandBallMode() {
			return hp;
		}

		public int getLeastVisibleBall() {
			for (int i = 1; i < bnm; i++)
				if (balls[i].visible)
					return balls[i].index;

			return -1;
		}

		public Shot getShot() {
			return shot;
		}

		public int getTurnNumber() {
			return turnNumber;
		}

		public boolean isSelecting() {
			return selecting;
		}

		public void setHandBallMode(int value) {
			hp = value;
		}

		public void setSelecting(boolean value) {
			selecting = value;
		}

		@Override
		public String toString() {
			return "GameState [turnNumber=" + turnNumber + ", currentTurn=" + currentTurn + ", balls=" + Arrays.toString(balls) + ", cue=" + cue + ", cueBallIndex=" + cueBallIndex + ", hp=" + hp
					+ ", shot=" + shot + "]";
		}
	}

	public class Shot {
		private int v;
		private int s;
		private int r_mv;
		private int r_ms;
		private int r_hv;
		private int r_hs;
		private int ri_h;
		private int ri_v;

		protected Shot(int v, int s, int r_mv, int r_ms, int r_hv, int r_hs, int ri_h, int ri_v) {
			this.v = v;
			this.s = s;
			this.r_mv = r_mv;
			this.r_ms = r_ms;
			this.r_hv = r_hv;
			this.r_hs = r_hs;
			this.ri_h = ri_h;
			this.ri_v = ri_v;
		}

		public int getR_HS() {
			return r_hs;
		}

		public int getR_HV() {
			return r_hv;
		}

		public int getR_MS() {
			return r_ms;
		}

		public int getR_MV() {
			return r_mv;
		}

		public int getRI_H() {
			return ri_h;
		}

		public int getRI_V() {
			return ri_v;
		}

		public int getS() {
			return s;
		}

		public int getV() {
			return v;
		}

		@Override
		public String toString() {
			return "Shot [v=" + v + ", s=" + s + ", r_mv=" + r_mv + ", r_ms=" + r_ms + ", r_hv=" + r_hv + ", r_hs=" + r_hs + ", ri_h=" + ri_h + ", ri_v=" + ri_v + "]";
		}
	}

	protected static double thma_i(double a) {
		return Math.round(a * 1000000) / 1000000;
	}

	protected BilliardsPlayer[] players;
	private boolean gameModified;
	private ArrayList<GameState> states;
	protected GameState currentState;

	protected int turnNumber;
	protected int bnm;
	protected int fd_w;
	protected int fd_h;
	protected int x_offs;
	protected int y_offs;
	protected int b_ct;
	protected int upk;
	protected int or;
	protected int ei;
	protected int bi;
	protected int whsm;
	protected int pw_cn;
	protected int pw_sd;
	protected int pdtf;
	protected int pi_e_p;
	protected int pi_e_m;
	protected int vc;
	protected int od;
	protected int od2;
	protected int ow;
	protected int ou;
	protected double ti;
	protected double te;
	protected int a_n;
	protected int a_x;
	protected int b_n;
	protected int b_x;
	protected int a_ct;
	protected int pch_1;
	protected int pch_2;
	protected Point2D[] initialPositions;

	protected int[] sqc;

	protected boolean cueBallPotted;

	protected int firstBallPotted;

	protected boolean firstBallFault;

	protected int firstBallHit;

	protected boolean changeTurn;

	protected boolean gameOver;

	protected int winner;

	protected boolean pottedMyBall;

	protected ArrayList<Integer> pottedBalls;

	protected BilliardsGame(BilliardsGameController controller, int[] sqc) {
		super(controller);

		this.sqc = sqc;

		players = (BilliardsPlayer[]) super.players;
		pottedBalls = new ArrayList<>();
		states = new ArrayList<>();
		currentState = null;
	}

	@Override
	protected boolean beforeStart() {
		if (!super.beforeStart())
			return false;

		fd_w = 74000;
		fd_h = 37000;
		x_offs = 3000;
		y_offs = 3000;

		initializeOffsets();

		pi_e_p = 15;
		pi_e_m = 5;
		vc = 2100;
		od = or * 2;
		od2 = od * 2;
		ow = od * od;
		ou = or * or;
		ti = 3.141;
		te = thma_i(ti / 180);
		a_n = or + x_offs;
		a_x = fd_w - or + x_offs;
		b_n = or + y_offs;
		b_x = fd_h - or + y_offs;
		a_ct = fd_w / 2 + x_offs;
		b_ct = fd_h / 2 + y_offs;
		int __reg10 = fd_w / 4;
		pch_1 = x_offs + __reg10;
		pch_2 = x_offs + __reg10 * 3;

		initialPositions = new Point2D[bnm];
		initializeBallPositions();

		states.clear();
		currentState = new GameState();
		states.add(currentState);

		for (int i = 0; i < players.length; i++) {
			BilliardsPlayer player = players[i];
			player.score = 0;
			player.ballIcon = -1;
			player.ballSet = initialBallSet();
			player.pottedBalls.clear();
		}

		gameModified = false;
		winner = -1;
		turnNumber = 0;

		return true;
	}

	protected void checkGame(BilliardsPlayer currentPlayer) {
		if (firstBallHit == -1 || !currentPlayer.bellongsToMyBallSet(firstBallHit)) {
			firstBallFault = true;
			currentState.hp = 1;
		}

		if (changeTurn && !cueBallPotted && !firstBallFault && pottedMyBall)
			changeTurn = false;
	}

	public boolean checkGameState(int cueBallIndex, int[] ballIndices, Point2D[] ballPositions, int[] pottedBallIndices, int[] pottedBallSlots, int[] hitBallIndices) {
		if (currentState == null)
			return false;

		if (currentState.cueBallIndex != cueBallIndex)
			return false;

		if (!Arrays.equals(ballIndices, currentState.ballIndices))
			return false;

		if (!Arrays.equals(ballPositions, currentState.ballPositions))
			return false;

		if (!Arrays.equals(pottedBallIndices, currentState.pottedBallIndices))
			return false;

		if (!Arrays.equals(pottedBallSlots, currentState.pottedBallSlots))
			return false;

		if (!Arrays.equals(hitBallIndices, currentState.hitBallIndices))
			return false;

		return false;
	}

	protected void checkHitBalls(BilliardsPlayer currentPlayer, int[] hitBallIndices) {
		if (hitBallIndices != null)
			if (hitBallIndices.length > 0)
				firstBallHit = hitBallIndices[0];
	}

	@SuppressWarnings("unused")
	protected void checkPottedBalls(BilliardsPlayer currentPlayer, int[] pottedBallIndices, int[] pottedBallSlots) {
		for (int i = 0; i < pottedBallIndices.length; i++) {
			int ballIndex = pottedBallIndices[i];
			if (ballIndex < 0 || ballIndex > bnm)
				continue;

			int slotIndex = pottedBallSlots[i];

			currentState.balls[ballIndex].visible = false;
			currentState.balls[ballIndex].modified = false;

			if (firstBallPotted == -1)
				firstBallPotted = ballIndex;

			if (ballIndex == currentState.cueBallIndex) {
				if (resetCueBallWhenPotted()) {
					currentState.balls[ballIndex].reset();
					currentState.hp = 1;
				} else
					pottedBalls.add(ballIndex);

				cueBallPotted = true;
				onPottedCueBall(currentPlayer);
			} else {
				pottedBalls.add(ballIndex);
				if (currentPlayer.bellongsToMyBallSet(ballIndex)) {
					pottedMyBall = true;
					onPottedMyBall(currentPlayer, ballIndex);
				} else
					onPottedOtherBall(currentPlayer, ballIndex);
			}
		}
	}

	@Override
	protected Player createPlayer(int playerIndex, String name) {
		return new BilliardsPlayer(this, playerIndex, name);
	}

	@Override
	protected Player[] createPlayers() {
		return new BilliardsPlayer[2];
	}

	public Ball getBall(int index) {
		return currentState != null ? currentState.balls[index] : null;
	}

	public int getBNM() {
		return bnm;
	}

	public Cue getCue() {
		return currentState != null ? currentState.cue : null;
	}

	public int getCueBallIndex() {
		return currentState != null ? currentState.cueBallIndex : -1;
	}

	@Override
	public BilliardsPlayer getCurrentPlayer() {
		return (BilliardsPlayer) super.getCurrentPlayer();
	}

	public GameState getCurrentState() {
		return currentState;
	}

	public int getHP() {
		return currentState != null ? currentState.hp : 0;
	}

	@Override
	public int getInitialTurn() {
		return 0;
	}

	public int getLeastVisibleBall() {
		return currentState != null ? currentState.getLeastVisibleBall() : -1;
	}

	@Override
	public int getMaxRounds() {
		return 0;
	}

	@Override
	public BilliardsPlayer getPlayer(int playerIndex) {
		return (BilliardsPlayer) super.getPlayer(playerIndex);
	}

	public Shot getShot() {
		return currentState != null ? currentState.shot : null;
	}

	public GameState getState(int turnNumber) {
		return states.get(turnNumber);
	}

	public int getTurnNumber() {
		return turnNumber;
	}

	public abstract int[] initialBallSet();

	public abstract int initialHandBallMode();

	protected abstract void initializeBallPositions();

	protected abstract void initializeOffsets();

	public boolean isGameModified() {
		return gameModified;
	}

	protected void onPottedMyBall(BilliardsPlayer currentPlayer, int ballIndex) {

	}

	protected void onPottedOtherBall(BilliardsPlayer currentPlayer, int ballIndex) {

	}

	protected void onPottedCueBall(BilliardsPlayer currentPlayer) {

	}

	protected boolean resetCueBallWhenPotted() {
		return true;
	}

	public void setBallVisible(int index, boolean value) {
		if (currentState != null) {
			Ball ball = currentState.balls[index];
			ball.visible = value;
			ball.modified = true;

			gameModified = true;
		}
	}

	public void shot(int v, int s, int r_mv, int r_ms, int r_hv, int r_hs, int ri_h, int ri_v) {
		if (currentState != null) {
			currentState.shot = new Shot(v, s, r_mv, r_ms, r_hv, r_hs, ri_h, ri_v);
			currentState.hp = 0;

			gameModified = true;
		}
	}

	@Override
	public void stop(StopReason reason) {
		if (!isRunning())
			return;

		if (reason != StopReason.CANCELED)
			if (reason == StopReason.RESIGN)
				for (BilliardsPlayer player : players)
					if (!player.isPlaying())
						player.reportLoser();
					else
						player.reportWinner();
			else
				for (BilliardsPlayer player : players)
					if (player.getIndex() == winner)
						player.reportWinner();
					else
						player.reportLoser();

		super.stop(reason);
	}

	@Override
	public String toString() {
		return "BilliardsGame [players=" + Arrays.toString(players) + ", gameModified=" + gameModified + ", states=" + states + ", currentState=" + currentState + ", turnNumber=" + turnNumber
				+ ", bnm=" + bnm + ", whiteBallPotted=" + cueBallPotted + ", firstBallPotted=" + firstBallPotted + ", firstBallFault=" + firstBallFault + ", firstBallHit=" + firstBallHit
				+ ", changeTurn=" + changeTurn + ", gameOver=" + gameOver + ", winner=" + winner + ", pottedMyBall=" + pottedMyBall + ", pottedBalls=" + pottedBalls + "]";
	}

	public void updateBall(int index, int x, int y) {
		if (currentState != null) {
			Ball ball = currentState.balls[index];
			ball.x = x;
			ball.y = y;
			ball.modified = true;

			gameModified = true;
		}
	}

	public void updateCue(int angle) {
		if (currentState != null) {
			currentState.cue.angle = angle;
			gameModified = true;
		}
	}

	public void updateCueBall(int cueBallIndex, int x, int y, int angle) {
		if (currentState != null) {
			currentState.cueBallIndex = cueBallIndex;
			currentState.cue.cueBallIndex = cueBallIndex;
			currentState.cue.x = x;
			currentState.cue.y = y;
			currentState.cue.angle = angle;

			currentState.hp = 0;

			gameModified = true;
		}
	}

	public void updateGameState(int cueBallIndex, int[] ballIndices, Point2D[] ballPositions, int[] pottedBallIndices, int[] pottedBallSlots, int[] hitBallIndices) {
		turnNumber++;
		currentState = new GameState(currentState);
		states.add(currentState);

		currentState.cueBallIndex = cueBallIndex;
		currentState.ballIndices = ballIndices;
		currentState.ballPositions = ballPositions;
		currentState.pottedBallIndices = pottedBallIndices;
		currentState.pottedBallIndices = pottedBallSlots;
		currentState.hitBallIndices = hitBallIndices;

		BilliardsPlayer currentPlayer = getCurrentPlayer();

		currentState.hp = 0;
		cueBallPotted = false;
		firstBallPotted = -1;
		firstBallFault = false;
		firstBallHit = -1;
		changeTurn = true;
		gameOver = false;
		pottedMyBall = false;
		pottedBalls.clear();

		if (ballIndices != null)
			for (int i = 0; i < ballIndices.length; i++) {
				int ballIndex = ballIndices[i];
				currentState.balls[ballIndex].x = ballPositions[i].getX();
				currentState.balls[ballIndex].y = ballPositions[i].getY();
				currentState.balls[ballIndex].modified = true;
			}

		if (pottedBallIndices != null)
			checkPottedBalls(currentPlayer, pottedBallIndices, pottedBallSlots);

		if (hitBallIndices != null)
			checkHitBalls(currentPlayer, hitBallIndices);

		checkGame(currentPlayer);

		Ball cb = currentState.balls[currentState.cueBallIndex];
		currentState.cue.cueBallIndex = cb.index;
		currentState.cue.x = cb.x;
		currentState.cue.y = cb.y;
		currentState.shot = null;
		gameModified = true;

		if (gameOver)
			stop(StopReason.NORMAL);
		else if (changeTurn) {
			nextTurn();
			currentState.currentTurn = getCurrentTurn();
		} else
			resetTimePerTurn();
	}
	
	public Point2D getRandomPosition() {
		return getRandomPosition(-1);
	}
	
	public Point2D getRandomPosition(int exclude) {
		while (true) {
			int x = RandomUtil.randomRange(a_n + 1, a_x - 1);
			int y = RandomUtil.randomRange(b_n + 1, b_x - 1);
			Point2D result = new Point2D(x, y);
			if (!isColliding(result, exclude))
				return result;
		}
	}
	
	public Point2D getRandomPositionAround(Point2D center) {
		return getRandomPositionAround(center, -1);
	}
	
	public Point2D getRandomPositionAround(Point2D center, int exclude) {
		for (int radius = od + 1; radius < a_x - a_n; radius += od) {
			int angle = RandomUtil.randomRange(0, 359);
			double radians = angle * Math.PI / 180;
			Point2D result = new Point2D(center.getX() + (int) (radius * Math.cos(radians)), center.getY() + (int) (radius * Math.sin(radians)));
			if (!isColliding(result, exclude))
				return result;
		}
		
		return getRandomPosition(exclude);
	}
	
	public boolean isColliding(Point2D position) {
		return isColliding(position, -1);
	}
	
	public boolean isColliding(Point2D position, int exclude) {
		int x = position.getX();
		int y = position.getY();
		
		for (int i = 0; i < bnm; i++) {
			if (i == exclude)
				continue;
			
			Ball ball = currentState.getBall(i);
			if (!ball.visible)
				continue;
			
			int a = ball.getX();
			int b = ball.getY();
			
			float dx = x - a;
			float dy = y - b;
			
			if (Math.sqrt(dx * dx + dy * dy) <= od)
				return true;
		}
		
		return false;
	}

}
