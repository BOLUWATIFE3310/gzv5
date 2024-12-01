package gz.server.net.billiards;

import gz.server.net.Connection;
import gz.server.net.Lobby;
import gz.util.GZStruct;

public class BilliardsLobby extends Lobby {

	public BilliardsLobby() {
		super();
	}

	@Override
	public BilliardsContainer getContainer() {
		return (BilliardsContainer) super.getContainer();
	}

	@Override
	protected void parseDataInternal(Connection user, String opcode, GZStruct request) throws Throwable {
		switch (opcode) {
			case "nwg": { // start single player game
				if (!connections.contains(user))
					break;

				String variant = request.getString("t");
				String p = request.getString("p");
				String[] parameters = p.split("_");

				boolean allowWatchers = parameters[0].equals("1");

				int[] options = new int[parameters.length - 1];
				for (int i = 0; i < parameters.length - 1; i++)
					options[i] = Integer.parseInt(parameters[i + 1]);

				if (!validateOptions(options)) {
					user.postClose();
					return;
				}

				if (options[1] == 0)
					createGame(variant, allowWatchers, options, true, user, user);
				else
					createGame(variant, allowWatchers, options, true, user);

				break;
			}

			default:
				super.parseDataInternal(user, opcode, request);
		}
	}

	@Override
	protected boolean validateOptions(int[] options) {
		return true;
	}

	@Override
	protected boolean validateVariant(String variant) {
		return variant.equals("8ball") || variant.equals("9ball") || variant.equals("straight") || variant.equals("gzpool") || variant.equals("snooker") || variant.equals("carom")
				|| variant.equals("pyramid") || variant.equals("svoi") || variant.equals("brpool");
	}

}
