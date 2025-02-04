package gz.common.logic;

import common.process.timer.Timer;
import gz.common.logic.Game.StopReason;
import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public abstract class Player {

	/**
	 * 
	 */
	MultiplayerGame game;
	int index;
	String name;
	boolean started;
	boolean playing;
	boolean abandoned;
	int result;

	Timer tmrTimer;
	boolean timerWasRunning;

	protected Player(MultiplayerGame game, int index, String name) {
		this.game = game;
		this.index = index;
		this.name = name;

		started = false;
		playing = false;
		result = MultiplayerGame.NO_RESULT;

		tmrTimer = new Timer(this.game.controller.getQueue(), this.game.getDefaultTime() * 1000, true);
		tmrTimer.addListener((timer, interal) -> this.game.onTimeOut());
	}

	public void cancelStart() {
		started = false;
	}

	public boolean canReceiveTurn() {
		return isPlaying();
	}

	public boolean canStart() {
		return !game.isRunning();
	}

	protected void close() {
		tmrTimer.close();

		game = null;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		
		if (obj == null)
			return false;
		
		if (!(obj instanceof Player))
			return false;
		
		Player other = (Player) obj;
		if (index != other.index)
			return false;
		
		return true;
	}

	public int getCurrentTime() {
		int t = tmrTimer.getInterval() - tmrTimer.getCurrentTime();
		return (t >= 0 ? t : 0) / 1000;
	}

	public MultiplayerGame getGame() {
		return game;
	}

	public int getIndex() {
		return index;
	}

	public String getName() {
		return name;
	}

	public int getResult() {
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + index;
		return result;
	}

	public boolean isAbandoned() {
		return abandoned;
	}

	public boolean isDraw() {
		return result == MultiplayerGame.DRAW;
	}

	public boolean isLoser() {
		return result == MultiplayerGame.LOSS;
	}

	public boolean isMyTurn() {
		return game.isRunning() && index == game.currentTurn;
	}

	public boolean isPlaying() {
		return game.isRunning() && playing;
	}

	public boolean isStarted() {
		return started;
	}

	public boolean isWinner() {
		return result == MultiplayerGame.WIN;
	}

	public void leave() {
		started = false;
		abandoned = true;

		if (game.isRunning())
			resign();
	}

	protected void reportDraw() {
		result = MultiplayerGame.DRAW;
	}

	protected void reportLoser() {
		result = MultiplayerGame.LOSS;
	}

	protected void reportWinner() {
		result = MultiplayerGame.WIN;
	}

	protected void reset() {
		result = MultiplayerGame.NO_RESULT;
		abandoned = false;
		
		if (started) {
			started = false;
			playing = true;
		}

		if (game.hasTime) {
			tmrTimer.setInterval(game.getTime() * 1000);
			tmrTimer.reset();
		}
	}

	public boolean resign() {
		if (!isPlaying())
			return false;

		playing = false;

		if (game.getPlayingCount() < 2)
			game.stop(StopReason.RESIGN);
		else
			game.nextTurn();

		return true;
	}

	public boolean start() {
		if (!canStart())
			return false;

		started = true;

		return true;
	}

	@Override
	public String toString() {
		return "Player [index=" + index + ", name=" + name + ", started=" + started + ", playing=" + playing + ", abandoned=" + abandoned + ", result=" + result + "]";
	}

}