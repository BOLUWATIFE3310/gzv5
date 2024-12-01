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

<tr>
	<td align="center" class="sub" height="60"
		background="/img/bgbottom.jpg"
		style="background-repeat: no-repeat; background-position: 100% 0%; padding-top: 30px;">
		<a href="<%=servlet.getHomeURL()%>"><%= servlet.getHomeName() %></a> 
		<script>
		  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
		  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
		  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
		  })(window,document,'script','https://www.google-analytics.com/analytics.js','ga');
		
		  ga('create', 'UA-85627835-1', 'auto');
		  ga('send', 'pageview');
		</script>
		<br><br>
	</td>
</tr>