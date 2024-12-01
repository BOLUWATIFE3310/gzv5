/**
 * 
 */
package gz.common.util;

import java.io.IOException;

import javax.servlet.jsp.JspWriter;

/**
 * @author Saddam Hussein
 * 
 */
public class Status {

	public static final void dumpThreadStack(JspWriter out) throws IOException {
		ThreadGroup group = Thread.currentThread().getThreadGroup();
		while (true) {
			ThreadGroup parent = group.getParent();
			if (parent == null)
				break;

			group = parent;
		}

		out.println("<ul>");
		dumpThreadStack(group, out);
		out.println("</ul>");
	}

	private static final void dumpThreadStack(ThreadGroup group, JspWriter out) throws IOException {
		int activeCount = group.activeCount();
		if (activeCount == 0)
			return;

		out.println("<li><strong>" + group.getName() + "</strong></li>");

		Thread[] threads = new Thread[activeCount];
		int threadCount = group.enumerate(threads, false);

		if (threadCount > 0) {
			out.println("<ul>");
			for (int i = 0; i < threadCount; i++) {
				Thread thread = threads[i];
				out.println("<li>Thread[" + thread.getName() + "][" + thread.getState() + "]</li>");
				StackTraceElement[] stackTrace = thread.getStackTrace();
				out.println("<ul>");
				for (StackTraceElement element : stackTrace)
					out.println("<li>" + element + "</li>");
				out.println("</ul>");
			}
			out.println("</ul");
		}

		ThreadGroup[] groups = new ThreadGroup[group.activeGroupCount()];
		int groupCount = group.enumerate(groups, false);

		if (groupCount > 0) {
			out.println("<ul>");
			for (int i = 0; i < groupCount; i++)
				dumpThreadStack(groups[i], out);

			out.println("</ul>");
		}
	}

}
