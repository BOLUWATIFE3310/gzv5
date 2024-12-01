package gz.common.io;

import java.io.File;

public interface DirectoryWatcherListener {
	
	void onCreate(File file);

	void onModify(File file);

	void onDelete(File file);
	
	void onException(Throwable e);

}
