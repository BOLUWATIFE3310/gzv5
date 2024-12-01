package gz.server.net.billiards;

import gz.server.net.Connection;

public class BilliardsConnection extends Connection {

	boolean wachingAnimation;
	boolean waitingForState;
	boolean updated;
	int turnNumber;

	public BilliardsConnection() {

	}

}
