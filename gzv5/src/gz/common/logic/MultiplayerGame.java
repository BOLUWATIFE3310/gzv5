package gz.common.logic;

import java.util.Arrays;
import java.util.List;

import common.process.timer.Timer;
import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public abstract class MultiplayerGame extends Game {

	private static final int MIN_TIME_PER_TURN = 5;
	private static final int MAX_TIME_PER_TURN = 30;
	private static final int DEFAULT_TIME_PER_TURN = 15;

	private static final int MIN_INCREMENT_TIME = 0;
	private static final int MAX_INCREMENT_TIME = 10;
	private static final int DEFAULT_INCREMENT_TIME = 1;

	private static final int MIN_TIME = 60;
	private static final int MAX_TIME = 1800;
	private static final int DEFAULT_TIME = 300;

	public static final int NO_RESULT = 0;
	public static final int WIN = 1;
	public static final int LOSS = 2;
	public static final int DRAW = 3;

	public static final int NO_TURN = 8;

	GameController controller;

	int currentTurn;
	private int round;
	protected Player[] players;

	private boolean hasTimePerTurn;
	private boolean hasIncrementTime;
	private int incrementTime;
	boolean hasTime;
	private int time;

	private Timer tmrPerMoveTurn;
	private boolean perMoveTurnTimerWasRunning;

	private int minTimePerTurn;
	private int maxTimePerTurn;
	private int minTime;
	private int maxTime;
	private int minIncrementTime;
	private int maxIncrementTime;

	public MultiplayerGame(GameController controller) {
		super();

		this.controller = controller;

		hasTimePerTurn = true;
		hasIncrementTime = true;
		hasTime = true;

		minTimePerTurn = MIN_TIME_PER_TURN;
		maxTimePerTurn = MAX_TIME_PER_TURN;
		minTime = MIN_TIME;
		maxTime = MAX_TIME;
		minIncrementTime = MIN_INCREMENT_TIME;
		maxIncrementTime = MAX_INCREMENT_TIME;

		players = createPlayers();

		tmrPerMoveTurn = new Timer(controller.getQueue(), getDefaultTimePerTurn() * 1000, true);
		tmrPerMoveTurn.addListener((timer, interal) -> onTurnTimeOut());

		incrementTime = getDefaultIncrementTime();

		time = getDefaultTime();

		round = 0;
		currentTurn = NO_TURN;
	}

	protected void afterNextTurn() {

	}

	protected void afterPreviousTurn() {

	}

	protected void afterStart() {
		for (Player player : players)
			if (player != null)
				player.reset();
	}

	protected boolean beforeNextTurn() {
		return true;
	}

	protected boolean beforePreviousTurn() {
		return true;
	}

	protected boolean beforeStart() {
		if (getStartedCount() < 2) {
			for (Player player : players)
				if (player != null)
					player.started = false;

			return false;
		}

		return true;
	}

	public boolean canJoin(int playerIndex, String name) {
		if (playerIndex < 0 || playerIndex >= players.length)
			return false;

		if (isRunning())
			return false;

		if (players[playerIndex] == null || players[playerIndex].abandoned)
			return true;

		return players[playerIndex].name.equalsIgnoreCase(name);
	}

	@Override
	public boolean close() {
		if (!super.close())
			return false;

		for (int playerIndex = 0; playerIndex < players.length; playerIndex++) {
			Player player = players[playerIndex];
			if (player != null) {
				player.close();
				players[playerIndex] = null;
			}
		}

		tmrPerMoveTurn.close();

		controller = null;

		return true;
	}

	protected abstract Player createPlayer(int playerIndex, String name);

	protected abstract Player[] createPlayers();

	public Player getCurrentPlayer() {
		return getPlayer(getCurrentTurn());
	}

	public int getCurrentTimePerTurn() {
		int t = tmrPerMoveTurn.getInterval() - tmrPerMoveTurn.getCurrentTime();
		return (t >= 0 ? t : 0) / 1000;
	}

	public int getCurrentTurn() {
		return currentTurn;
	}

	public int getDefaultIncrementTime() {
		return DEFAULT_INCREMENT_TIME;
	}

	public int getDefaultTime() {
		return DEFAULT_TIME;
	}

	public int getDefaultTimePerTurn() {
		return DEFAULT_TIME_PER_TURN;
	}

	public int getIncrementTime() {
		return incrementTime;
	}

	public abstract int getInitialTurn();

	public int getLoserBits() {
		int result = 0;
		for (Player player : players)
			if (player != null && player.isLoser())
				result |= 1 << player.getIndex();

		return result;
	}

	public int getMaxIncrementTime() {
		return maxIncrementTime;
	}

	public final int getMaxPlayers() {
		return players.length;
	}

	public abstract int getMaxRounds();

	public int getMaxTime() {
		return maxTime;
	}

	public int getMaxTimePerTurn() {
		return maxTimePerTurn;
	}

	public int getMinIncrementTime() {
		return minIncrementTime;
	}

	public int getMinTime() {
		return minTime;
	}

	public int getMinTimePerTurn() {
		return minTimePerTurn;
	}

	public Player getNextPlayer() {
		return getPlayer(getNextTurn());
	}

	public Player getNextPlayer(int current) {
		return getPlayer(getNextTurn(current));
	}

	public int getNextTurn() {
		return getNextTurn(getCurrentTurn());
	}

	public int getNextTurn(int current) {
		if (!isRunning() || current == NO_TURN)
			return NO_TURN;

		int turn = current;
		do {
			turn++;
			turn %= getMaxPlayers();
		} while (turn != current && (players[turn] == null || !players[turn].canReceiveTurn()));

		return turn;
	}

	public Player getPlayer(int playerIndex) {
		if (playerIndex < 0 || playerIndex >= getMaxPlayers())
			return null;

		return players[playerIndex];
	}

	public int getPlayerBits() {
		int result = 0;
		for (Player player : players)
			if (player != null)
				result |= 1 << player.getIndex();

		return result;
	}

	public int getPlayerCount() {
		int result = 0;
		for (Player player : players)
			if (player != null)
				result++;

		return result;
	}

	public int getPlayingCount() {
		int result = 0;
		for (Player player : players)
			if (player != null && player.isPlaying())
				result++;

		return result;
	}

	public int getPlayngBits() {
		int result = 0;
		for (Player player : players)
			if (player != null && player.isPlaying())
				result |= 1 << player.getIndex();

		return result;
	}

	public Player getPreviousPlayer() {
		return getPlayer(getPreviousTurn());
	}

	public Player getPreviousPlayer(int current) {
		return getPlayer(getPreviousTurn(current));
	}

	public int getPreviousTurn() {
		return getPreviousTurn(getCurrentTurn());
	}

	public int getPreviousTurn(int current) {
		if (!isRunning() || current == NO_TURN)
			return NO_TURN;

		int turn = current;
		do {
			turn--;
			if (turn < 0)
				turn += getMaxPlayers();
			turn %= getMaxPlayers();
		} while (turn != current && (players[turn] == null || !players[turn].canReceiveTurn()));

		return turn;
	}

	public int getReceiveTurnCount() {
		return getReceiveTurnCount(null);
	}

	public int getReceiveTurnCount(Player except) {
		int result = 0;
		for (Player player : players)
			if (player != null && !player.equals(except) && player.canReceiveTurn())
				result++;

		return result;
	}

	public int getRound() {
		return round;
	}

	public int getStartedCount() {
		int result = 0;
		for (Player player : players)
			if (player != null && player.isStarted())
				result++;

		return result;
	}

	public int getTime() {
		return time;
	}

	public int getTimePerTurn() {
		return tmrPerMoveTurn.getInterval() / 1000;
	}

	public int getWinnerBits() {
		int result = 0;
		for (Player player : players)
			if (player != null && player.isWinner())
				result |= 1 << player.getIndex();

		return result;
	}

	public boolean hasIncrementTime() {
		return hasIncrementTime;
	}

	public boolean hasTime() {
		return hasTime;
	}

	public boolean hasTimePerTurn() {
		return hasTimePerTurn;
	}

	public Player join(int playerIndex, String name) {
		if (!canJoin(playerIndex, name))
			return null;

		if (players[playerIndex] != null && players[playerIndex].name.equalsIgnoreCase(name)) {
			players[playerIndex].abandoned = false;

			return players[playerIndex];
		}

		players[playerIndex] = createPlayer(playerIndex, name);

		return players[playerIndex];
	}

	public void nextRound() {
		round++;
		processRound(round);

		if (controller != null)
			controller.onNextRound(round);
	}

	public final void nextTurn() {
		if (!beforeNextTurn())
			return;
		
		for (Player player: players)
			if (player != null)
				player.timerWasRunning = false;

		Player player = getCurrentPlayer();
		if (player != null) {
			player.tmrTimer.pause();
			if (hasIncrementTime)
				player.tmrTimer.setCurrentTime(player.tmrTimer.getCurrentTime() - incrementTime);
		}

		currentTurn = getNextTurn(currentTurn);

		player = getCurrentPlayer();
		if (hasTime && player != null)
			player.tmrTimer.play();

		perMoveTurnTimerWasRunning = false;
		if (hasTimePerTurn)
			tmrPerMoveTurn.reset();

		if (getPlayingCount() < 2) {
			stop();

			return;
		}

		afterNextTurn();

		if (isRunning() && controller != null)
			controller.onChangeTurn(currentTurn);
	}

	protected void onPlayerTurnTimeOut(Player player) {
		if (player != null)
			player.resign();
	}

	protected void onResetTurn() {

	}

	void onTimeOut() {
		if (!isRunning())
			return;

		Player player = getPlayer(currentTurn);
		if (player != null)
			player.resign();
	}

	private void onTurnTimeOut() {
		if (!isRunning())
			return;

		onPlayerTurnTimeOut(getPlayer(currentTurn));
	}

	protected void pauseAllTimers() {
		if (!tmrPerMoveTurn.isPaused()) {
			perMoveTurnTimerWasRunning = true;
			tmrPerMoveTurn.pause();
		} else
			perMoveTurnTimerWasRunning = false;

		for (Player player : players)
			if (player != null)
				if (!player.tmrTimer.isPaused()) {
					player.timerWasRunning = true;
					player.tmrTimer.pause();
				} else
					player.timerWasRunning = false;
	}

	public List<Player> players() {
		return Arrays.asList(players);
	}

	public final void previousTurn() {
		if (!beforePreviousTurn())
			return;
		
		for (Player player: players)
			if (player != null)
				player.timerWasRunning = false;

		Player player = getCurrentPlayer();
		if (player != null) {
			player.tmrTimer.pause();
			if (hasIncrementTime)
				player.tmrTimer.setCurrentTime(player.tmrTimer.getCurrentTime() - incrementTime);
		}

		currentTurn = getPreviousTurn(currentTurn);

		perMoveTurnTimerWasRunning = false;
		player = getCurrentPlayer();
		if (hasTime && player != null)
			player.tmrTimer.play();

		if (hasTimePerTurn)
			tmrPerMoveTurn.reset();

		if (getPlayingCount() < 2) {
			stop();

			return;
		}

		afterPreviousTurn();

		if (isRunning() && controller != null)
			controller.onChangeTurn(currentTurn);
	}

	protected void processRound(int round) {
		if (!isRunning())
			return;

		if (getMaxRounds() > 0 && round == getMaxRounds() || getPlayingCount() < 2)
			stop();

		resetTurn();
	}

	public void resetTimePerTurn() {
		if (hasTimePerTurn)
			tmrPerMoveTurn.reset();
	}

	public void resetTurn() {
		currentTurn = getInitialTurn();
		if (hasTime)
			players[currentTurn].tmrTimer.play();

		onResetTurn();

		if (controller != null)
			controller.onChangeTurn(currentTurn);
	}

	protected void resumeAllTimers() {
		if (perMoveTurnTimerWasRunning) {
			tmrPerMoveTurn.play();
			perMoveTurnTimerWasRunning = false;
		}
		for (Player player : players)
			if (player != null && player.timerWasRunning) {
				player.tmrTimer.play();
				player.timerWasRunning = false;
			}
	}

	public void rotateLeft() {
		if (players.length < 2)
			return;

		Player first = players[0];
		for (int index = 0; index < players.length - 1; index++) {
			players[index] = players[index + 1];
			if (players[index] != null)
				players[index].index = index;
		}

		players[players.length - 1] = first;
		if (first != null)
			first.index = players.length - 1;

		if (controller != null)
			controller.onRotateLeft();
	}

	public void rotateRight() {
		if (players.length < 2)
			return;

		if (players.length < 2)
			return;

		Player last = players[players.length - 1];
		for (int index = players.length - 1; index > 0; index--) {
			players[index] = players[index - 1];
			if (players[index] != null)
				players[index].index = index;
		}

		players[0] = last;
		if (last != null)
			last.index = 0;

		if (controller != null)
			controller.onRotateRight();
	}

	public void setCurrentTurn(int turn) {
		currentTurn = turn;
	}

	public void setIncrementTime(int incrementTime) {
		if (validateIncrementTime(incrementTime))
			this.incrementTime = incrementTime;
	}

	public void setInitialTime(int time) {
		if (validateTime(time))
			this.time = time;
	}

	public void setMaxIncrementTime(int maxIncrementTime) {
		this.maxIncrementTime = maxIncrementTime;
	}

	public void setMaxTime(int value) {
		maxTime = value;
	}

	public void setMaxTimePerTurn(int value) {
		maxTimePerTurn = value;
	}

	public void setMinIncrementTime(int minIncrementTime) {
		this.minIncrementTime = minIncrementTime;
	}

	public void setMinTime(int value) {
		minTime = value;
	}

	public void setMinTimePerTurn(int value) {
		minTimePerTurn = value;
	}

	public void setTimePerTurn(int timePerMove) {
		if (validatePerTurnTime(timePerMove))
			tmrPerMoveTurn.setInterval(timePerMove * 1000);
	}

	public void setUseIncrementTime(boolean hasIncrementTime) {
		this.hasIncrementTime = hasIncrementTime;
	}

	public void setUseTime(boolean hasTime) {
		if (!hasTime && this.hasTime)
			for (Player player : players)
				if (player != null)
					player.tmrTimer.pause();

		this.hasTime = hasTime;
	}

	public void setUseTimePerTurn(boolean hasTimePerMove) {
		if (!hasTimePerMove && hasTimePerTurn)
			tmrPerMoveTurn.pause();

		hasTimePerTurn = hasTimePerMove;
	}

	@Override
	public final boolean start() {
		if (isRunning())
			return false;

		for (Player player : players)
			if (player != null && player.isAbandoned())
				players[player.getIndex()] = null;

		if (!beforeStart())
			return false;

		super.start();

		if (controller != null)
			controller.onStart();

		if (hasTimePerTurn) {
			tmrPerMoveTurn.reset();
			tmrPerMoveTurn.play();
		}

		afterStart();

		round = 0;
		processRound(round);

		if (controller != null)
			controller.onStarted();

		return true;
	}

	@Override
	public void stop(StopReason reason) {
		if (!isRunning())
			return;

		perMoveTurnTimerWasRunning = false;
		tmrPerMoveTurn.pause();

		for (Player player : players)
			if (player != null) {
				player.playing = false;
				player.started = false;
				player.tmrTimer.pause();
				player.timerWasRunning = false;
			}

		currentTurn = NO_TURN;

		super.stop(reason);

		if (controller != null)
			controller.onChangeTurn(currentTurn);

		if (controller != null)
			controller.onStop(reason);
	}

	public void swapSides(int index1, int index2) {
		Player temp = players[index1];
		players[index1] = players[index2];
		players[index2] = temp;

		if (players[index1] != null)
			players[index1].index = index1;

		if (players[index2] != null)
			players[index2].index = index2;

		if (controller != null)
			controller.onSwap(index1, index2);
	}

	protected boolean validateIncrementTime(int incrementTime) {
		return incrementTime >= getMinIncrementTime() && incrementTime <= getMaxIncrementTime();
	}

	private boolean validatePerTurnTime(int time) {
		return time >= getMinTimePerTurn() && time <= getMaxTimePerTurn();
	}

	private boolean validateTime(int time) {
		return time >= getMinTime() && time <= getMaxTime();
	}

}
