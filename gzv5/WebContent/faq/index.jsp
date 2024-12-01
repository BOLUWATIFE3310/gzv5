<%@ page language="java" contentType="text/html; charset=iso-8859-1"
	pageEncoding="iso-8859-1"%>
<%@ page import="gz.server.net.*"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<%
String lang = request.getQueryString();
if (lang == null)
	lang = request.getLocale().toLanguageTag();

Container servlet = (Container) application.getAttribute("billiards");
if (servlet == null)
	return;

String STV = servlet.getSTV();
String homeversion = servlet.getHomeVersion();
String gameversion = servlet.getGameVersion();
String smenuversion = servlet.getSMenuVersion();
String domainhost = servlet.getDomainHost();
String homename = servlet.getHomeName();
String homeurl = servlet.getHomeURL();
String contenturl = servlet.getContentURL();
String forumurl = "http://vip.gamezergalaxy.com/forum/1045-gz-team-support/";
String serverurl = servlet.getServerURL();
String swfdir = servlet.getSWFDir();

pageContext.setAttribute("localeCode", lang);
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<html>
<head>

<title><%= servlet.getHomeName() %> | FAQ
</title>
<meta name="title" content="<%= servlet.getHomeName() %> - Online Multiplayer Games">
<meta name="description" content="Checkers. Multiplayer Online games.">
<meta name="keywords" content="Checkers Multiplayer game">
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">
<meta name="author" content="<%= servlet.getHomeName() %>">
<meta name="abstract" content="<%= servlet.getDomainHost() %>">
<meta name="resource-type" content="document">
<meta name="robots" content="all">
<link rel="icon" href="/favicon.ico" type="image/x-icon">
<style>
body {
	margin: 0 0 0 0;
	color: #ffffff;
	font-family: verdana, arial, sans-serif;
	font-size: 11px;
}

a {
	color: #ffffff;
	font-family: verdana, arial, sans-serif;
	font-size: 11px;
	text-decoration: underline;
}

a:hover {
	color: #ffffff;
	font-family: verdana, arial, sans-serif;
	font-size: 11px;
	text-decoration: none;
}

.bc {
	color: #CAFBB1;
	font-family: verdana, arial, sans-serif;
	font-size: 11px;
	text-decoration: none;
}

.bc a {
	color: #CAFBB1;
	font-family: verdana, arial, sans-serif;
	font-size: 11px;
	text-decoration: none;
}

.bc a:hover {
	color: #CAFBB1;
	font-family: verdana, arial, sans-serif;
	font-size: 11px;
	text-decoration: underline;
}
</style>
</head>
<body bgcolor="#000000" marginheight="0" marginwidth="0" >
	<div align="center">
		<table cellpadding="0" cellspacing="0" align="center" width="1000"
			height="100" border="0">
			<tr>
				<td valign="top" colspan="5" width="728" height="90" align="center">
					<script async src="//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
					<!-- atrativa.net.br -->
					<ins class="adsbygoogle"
						 style="display:inline-block;width:728px;height:90px"
						 data-ad-client="ca-pub-6386041731893209"
						 data-ad-slot="2414174979"></ins>
					<script>
					(adsbygoogle = window.adsbygoogle || []).push({});
					</script>
				</td>
				<td width="58"><img src="/img/empty.gif" alt="" width="58"
					height="1" border="0"><br></td>
			</tr>
			
			<tr>
				<td valign="middle" colspan="5" align="center">
					<span> 
						<b> 
							<c:choose>
								<c:when test="${localeCode == 'pt'}">
									<c:import var="msg" url="include/lang/pt.jsp" />
									<c:out value="${msg}" escapeXml="false" />
								</c:when>
								<c:when test="${localeCode == 'pt-BR'}">
									<c:import var="msg" url="include/lang/pt.jsp" />
									<c:out value="${msg}" escapeXml="false" />
								</c:when>
								<c:when test="${localeCode == 'es'}">
									<c:import var="msg" url="include/lang/es.jsp" />
									<c:out value="${msg}" escapeXml="false" />
								</c:when>
								<c:otherwise>
									<c:import var="msg" url="include/lang/en.jsp" />
									<c:out value="${msg}" escapeXml="false" />
								</c:otherwise>
							</c:choose>
						</b>
					</span>
					<br> <br> <br>
				</td>
			</tr>
			
			<tr valign="top">
				<td colspan="5" class="bc" align="center"><br> <br> <a
					href="<%=servlet.getHomeURL()%>"><b><%=servlet.getHomeName()%></b></a>
					&nbsp; &#8226; &nbsp; <a href="/billiards/"><b>Billiards</b></a>
					&nbsp; &#8226; &nbsp; <a href="/chess/"><b>Chess</b></a> &nbsp;
					&#8226; &nbsp; <a href="/checkers/"><b>Checkers</b></a> &nbsp;
					<!--&#8226; &nbsp; <a href="/gladiator/"><b>Gladiator</b></a> &nbsp;-->
					&#8226; &nbsp; <a href="<%= forumurl %>" target="_blank"><b>Support</b></a></td>
			</tr>
			
			<% if (!domainhost.equals("localhost")) { %>
			<tr>
				<td valign="bottom" align="center" colspan="3" >
					<script async src="//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
					<!-- gamez_responsive -->
					<ins class="adsbygoogle"
						 style="display:block"
						 data-ad-client="ca-pub-6386041731893209"
						 data-ad-slot="6075331773"
						 data-ad-format="auto"></ins>
					<script>
					(adsbygoogle = window.adsbygoogle || []).push({});
					</script>
				</td>
			</tr>
			<% } %>
		</table>
		<script>
		  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
		  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
		  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
		  })(window,document,'script','https://www.google-analytics.com/analytics.js','ga');

		  ga('create', 'UA-85627835-1', 'auto');
		  ga('send', 'pageview');

		</script>
		<br>
		<br>
	</div>
	<script>
	<!--
		setTimeout("init_m()", 10000);
	//-->
	</script>
</body>
</html>