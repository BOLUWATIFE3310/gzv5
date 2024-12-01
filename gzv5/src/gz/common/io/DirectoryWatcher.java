package gz.common.io;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

public class DirectoryWatcher {

	private WatchService service;
	private Thread thread;

	public DirectoryWatcher(ThreadGroup group, String fileName, DirectoryWatcherListener listener) throws IOException {
		Path path = FileSystems.getDefault().getPath(fileName);
		
		try {
			Boolean isFolder = (Boolean) Files.getAttribute(path, "basic:isDirectory", NOFOLLOW_LINKS);
			if (!isFolder)
				throw new IllegalArgumentException("Path: " + path + " is not a folder");
		} catch (IOException ioe) {
			// Folder does not exists
			ioe.printStackTrace();
		}

		// We obtain the file system of the Path
		FileSystem fs = path.getFileSystem();

		service = fs.newWatchService();

		thread = new Thread(group, new Runnable() {

			@Override
			public void run() {
				// We create the new WatchService using the new try() block
				try {
					// We register the path to the service
					// We watch for creation events
					path.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

					// Start the infinite polling loop
					WatchKey key = null;
					while (true) {
						key = service.take();

						// Dequeueing events
						Kind<?> kind = null;
						for (WatchEvent<?> watchEvent : key.pollEvents()) {
							// Get the type of the event
							kind = watchEvent.kind();
							if (OVERFLOW == kind)
								continue; // loop
							
							@SuppressWarnings("unchecked")
							Path newPath = ((WatchEvent<Path>) watchEvent).context();

							if (kind == ENTRY_CREATE) {
								if (listener != null)
									listener.onCreate(newPath.toFile());
							} else if (kind == ENTRY_MODIFY) {
								if (listener != null)
									listener.onModify(newPath.toFile());
							} else if (kind == ENTRY_DELETE) {
								if (listener != null)
									listener.onDelete(newPath.toFile());
							}
						}

						if (!key.reset())
							break; // loop
					}

				} catch (Throwable e) {
					if (listener != null)
						listener.onException(e);
				}
			}
		}, path + " directory watcher");
		thread.start();
	}

	public void close() {
		try {
			service.close();
		} catch (IOException e) {
		}

		thread.interrupt();
	}

}
