<%@ page language="java" contentType="text/html; charset=iso-8859-1"
	pageEncoding="iso-8859-1"%>
<%@ page import="gz.server.net.*"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<%
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
String serverurl = servlet.getServerURL();
String swfdir = servlet.getSWFDir();

pageContext.setAttribute("localeCode", request.getLocale().toLanguageTag());
%>

<!DOCTYPE html>

<html>
<head>

<title><%= servlet.getHomeName() %> | <%= servlet.getTitle() %>
	Online Game</title>
<meta name="title"
	content="<%= servlet.getHomeName() %> - Online Multiplayer Games">
<meta name="description" content="Billiards Multiplayer Online games.">
<meta name="keywords" content="Billiards Multiplayer game">
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">
<meta name="author" content="<%= servlet.getHomeName() %>">
<meta name="abstract" content="<%= servlet.getDomainHost() %>">
<meta name="resource-type" content="document">
<meta name="robots" content="all">
<link rel="icon" href="/favicon.ico" type="image/x-icon">
<script>
//<!--
var mset = 0;
var smj = "";
if(this!=top){
	top.location.href = "";
}
function init_m(){
	if(mset){
		return;
	}
	mset = 1;
	var vp = 'setlang=en&z=billiards';
	if (document.all){
		smj = document.all.ins_flash;
	}else if (document.getElementById){  
		smj = document.getElementById("ins_flash");
	}else{  
		smj = document.ins_flash;  
	}
	var STV = 'v5_cv20';
		smj.innerHTML = '<object classid="clsid:d27cdb6e-ae6d-11cf-96b8-444553540000" codebase="http://fpdownload.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=9,0,115,0" width="1000" height="500" id="flashindex" align="middle">'
				+ '<param name="allowScriptAccess" value="always" />'
				+ '<param name="menu" value="false" />'
				+ '<param name="flashvars" value="'+vp+'" />'
				+ '<param name="movie" value="/swf_v5/base/flashindex_'+STV+'.swf" />'
				+ '<param name="quality" value="high" />'
				+ '<param name="bgcolor" value="#000000" />'
				+ '<embed src="http://content.gamezer.com/swf_v5/base/flashindex_'+STV+'.swf" flashvars="'+vp+'" menu="false" quality="high" bgcolor="#000000" width="1000" height="500" name="flashindex" align="middle" allowScriptAccess="always" type="application/x-shockwave-flash" pluginspage="http://www.macromedia.com/go/getflashplayer" />'
				+ '</object>';
	}
//-->
</script>
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
<body bgcolor="#000000" marginheight="0" marginwidth="0"
	onload="init_m()">
	<div align="center">
		<table cellpadding="0" cellspacing="0" align="center" width="1000"
			height="100" border="0">
			<tr>
				<td width="58">
					<img src="/img/empty.gif" alt="" width="58" height="1" border="0"><br>
				</td>
			</tr>
			
			<tr>
				<td valign="middle" align="center" colspan="3" id="ins_flash" height="500">
					<noscript>Please enable javascript!</noscript>
				</td>
			</tr>
			
			<tr valign="top">
				<td colspan="5" class="bc" align="center"><br> <br> <a
					href="<%=servlet.getHomeURL()%>"><b><%=servlet.getHomeName()%></b></a>
					&nbsp; &#8226; &nbsp; <a href="/billiards/"><b>Billiards</b></a>
					&nbsp; &#8226; &nbsp; <a href="/chess/"><b>Chess</b></a> &nbsp;
					&#8226; &nbsp; <a href="/checkers/"><b>Checkers</b></a> &nbsp; <!--&#8226; &nbsp; <a href="/gladiator/"><b>Gladiator</b></a> &nbsp;-->
					&#8226; &nbsp; <a href="/support/" target="_blank"><b>Support</b></a></td>
			</tr>
			
			<tr valign="top">
				<td height="10" colspan="5">&nbsp;</td>
			</tr>
			
			<tr>
				<td valign="top" colspan="5" align="center">
					<table cellpadding="6" cellspacing="0" align="center" border="0">
						<tr valign="middle">
							<td class="bc" align="left">&nbsp; &#8226; &nbsp; <a
								target="_blank" href="/top/?g=billiards&amp;v=gzpool">GZ
									Pool Top 100</a><br> &nbsp; &#8226; &nbsp; <a target="_blank"
								href="/rules/?g=billiards&amp;v=gzpool">GZ Pool Rules</a><br>
							</td>
							<td class="bc" align="left">&nbsp; &#8226; &nbsp; <a
								target="_blank" href="/top/?g=billiards&amp;v=8ball">8-Ball
									Top 100</a><br> &nbsp; &#8226; &nbsp; <a target="_blank"
								href="/rules/?g=billiards&amp;v=8ball">8-Ball Rules</a><br>
							</td>
							<td class="bc" align="left">&nbsp; &#8226; &nbsp; <a
								target="_blank" href="/top/?g=billiards&amp;v=9ball">9-Ball
									Top 100</a><br> &nbsp; &#8226; &nbsp; <a target="_blank"
								href="/rules/?g=billiards&amp;v=9ball">9-Ball Rules</a><br>
							</td>
							<td class="bc" align="left">&nbsp; &#8226; &nbsp; <a
								target="_blank" href="/top/?g=billiards&amp;v=straight">Straight
									Pool Top 100</a><br> &nbsp; &#8226; &nbsp; <a target="_blank"
								href="/rules/?g=billiards&amp;v=straight">Straight Pool
									Rules</a><br>
							</td>
							<td class="bc" align="left">&nbsp; &#8226; &nbsp; <a
								target="_blank" href="/top/?g=billiards&amp;v=snooker">Snooker
									Top 100</a><br> &nbsp; &#8226; &nbsp; <a target="_blank"
								href="/rules/?g=billiards&amp;v=snooker">Snooker Rules</a><br>
							</td>
							<td class="bc" align="left">&nbsp; &#8226; &nbsp; <a
								target="_blank" href="/top/?g=billiards&amp;v=pyramid">Pyramid
									Top 100</a><br> &nbsp; &#8226; &nbsp; <a target="_blank"
								href="/rules/?g=billiards&amp;v=pyramid">Pyramid Rules</a><br>
							</td>
							<td class="bc" align="left">&nbsp; &#8226; &nbsp; <a
								target="_blank" href="/top/?g=billiards&amp;v=carom">Carom
									Top 100</a><br> &nbsp; &#8226; &nbsp; <a target="_blank"
								href="/rules/?g=billiards&amp;v=carom">Carom Rules</a><br>
							</td>
						</tr>
					</table>
				</td>
			</tr>
		</table>
		<script>
		  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
		  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
		  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
		  })(window,document,'script','https://www.google-analytics.com/analytics.js','ga');

		  ga('create', 'UA-85627835-1', 'auto');
		  ga('send', 'pageview');
		</script>
		<br> <br>
	</div>
	<script>
	<!--
		setTimeout("init_m()", 10000);
	//-->
	</script>
</body>
</html>