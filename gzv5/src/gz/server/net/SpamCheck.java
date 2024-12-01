package gz.server.net;

import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public class SpamCheck {

	private int maxMessages;
	private int interval;
	private SpamAction action;

	private long lastCheck;
	private int messages;
	private boolean blocked;
	private long blockedWhen;
	private int blockedTime;

	public SpamCheck(int maxMessages, int interval, SpamAction action) {
		this.maxMessages = maxMessages;
		this.interval = interval;
		this.action = action;

		lastCheck = -1;
		messages = 0;
		blocked = false;
	}

	public void block() {
		block(0);
	}

	public synchronized void block(int time) {
		blockedWhen = System.currentTimeMillis();
		blockedTime = time;
		blocked = true;
	}

	public boolean check() {
		boolean spamDetected = false;
		try {
			synchronized (this) {
				if (isBlocked())
					return true;

				long time = System.currentTimeMillis();
				messages++;

				if (lastCheck == -1) {
					lastCheck = time;
					return true;
				}

				int delta = (int) (time - lastCheck);
				if (messages >= maxMessages || delta >= interval) {
					int messages1 = messages;
					messages = 0;
					lastCheck = time;
					if ((float) messages1 / (float) delta > (float) maxMessages / (float) interval) {
						spamDetected = true;
						return false;
					}
				}

				return true;
			}
		} finally {
			if (spamDetected && action != null)
				action.onSpamDetected(this);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;

		if (obj == null)
			return false;

		if (!(obj instanceof SpamCheck))
			return false;

		SpamCheck other = (SpamCheck) obj;
		if (action == null) {
			if (other.action != null)
				return false;
		} else if (!action.equals(other.action))
			return false;

		if (interval != other.interval)
			return false;

		if (maxMessages != other.maxMessages)
			return false;

		return true;
	}

	public synchronized SpamAction getAction() {
		return action;
	}

	public synchronized int getInterval() {
		return interval;
	}

	public synchronized int getMaxMessages() {
		return maxMessages;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (action == null ? 0 : action.hashCode());
		result = prime * result + interval;
		result = prime * result + maxMessages;
		return result;
	}

	public synchronized boolean isBlocked() {
		if (!blocked)
			return false;

		if (blockedTime == 0)
			return true;

		long now = System.currentTimeMillis();
		if (now >= blockedWhen + blockedTime) {
			blocked = false;
			return false;
		}

		return true;
	}

	public synchronized void setAction(SpamAction action) {
		this.action = action;
	}

	public synchronized void setInterval(int interval) {
		this.interval = interval;
	}

	public void setMaxMessages(int maxMessages) {
		this.maxMessages = maxMessages;
	}

	public boolean toggleBlock() {
		blocked = !blocked;
		return blocked;
	}

	@Override
	public String toString() {
		return maxMessages + "/" + interval;
	}

	public void unblock() {
		blocked = false;
	}

	public void reset() {
		messages = 0;
	}

}
