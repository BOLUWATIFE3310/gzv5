<%@ page language="java" contentType="text/html; charset=utf-8"
	pageEncoding="utf-8"%>
<%@ page
	import="java.util.*, java.sql.*, gz.server.net.*, gz.server.net.Container.*"%>

<%
	String game = request.getParameter("g");
	if (game == null)
		game = "billiards";

	Container servlet = (Container) application.getAttribute(game);
	if (servlet == null)
		return;
%>

<td align="left" width="230" background="/img/bs_left.jpg" style="background-repeat: no-repeat">
	<table cellpadding="0" cellspacing="0" height="100%" width="230" height="100%" border="0">
		<tr align="center" valign="top" width="100%" height="100" >
			<td align="center" valign="top" class="mv" style="padding-left: 20px; padding-right: 20px;">Users Online:
				<b><%=servlet.getOnlineUsers()%></b> <br> Registered: <b><%=servlet.getRegisteredUsers()%></b>
				<br> <br> <br> <br> <br> <br> <br>
			</td>
		</tr>
		
		<tr align="center" valign="top" width="100%" width="230">
			<td align="center" valign="top" width="120" >
				<script async src="//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
				<!-- gamez_160x600_3 -->
				<ins class="adsbygoogle"
					 style="display:inline-block;width:160px;height:600px"
					 data-ad-client="ca-pub-6386041731893209"
					 data-ad-slot="6057678577"></ins>
				<script>
				(adsbygoogle = window.adsbygoogle || []).push({});
				</script>
			</td>
		</td>
	</table>
</td>