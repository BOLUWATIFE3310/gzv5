<%@ page language="java" contentType="text/html; charset=utf-8"
	pageEncoding="utf-8"%>
<%@ page
	import="java.util.*, java.net.*, java.sql.*, gz.server.net.*, gz.server.net.Container.*"%>

<%
	Container servlet = (Container) application.getAttribute("billiards");
	if (servlet == null)
		return;
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<title><%= servlet.getHomeName() %> | Multiplayer Online Games</title>
<meta name="title" content="<%= servlet.getHomeName() %> - Multiplayer Online Games">
<meta name="description"
	content="<%= servlet.getHomeName() %> : Multiplayer Online games. Online Flash games, billiards, pool, checkers, chess, dominoes, gladiator">
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<meta name="author" content="<%=servlet.getHomeName()%>">
<meta name="abstract" content="<%=servlet.getDomainHost()%>">
<meta name="resource-type" content="document">
<meta name="url" content="<%=servlet.getHomeURL()%>">
<meta name="robots" content="all">
<link type="text/css" href="/cssp.css" rel="stylesheet">
<script>
<!--
	if (this != top) {
		top.location.href = "";
	}
//-->
</script>
</head>

<body bgcolor="#000000" marginheight="0" marginwidth="0"
	style="margin: 0 0 0 0;">
	<table cellpadding="0" cellspacing="0" align="center" height="100%"
		width="100%" border="0">
		<tr>
			<td rowspan="3" background="/img/sm_left.jpg"
				style="background-repeat: no-repeat; background-position: 100% 0%;">
				&nbsp;
			</td>
			<td valign="top" width="986" height="530"
				background="/img/gzmain_base.jpg"
				style="background-repeat: no-repeat">
				<table cellpadding="0" cellspacing="0" align="center" width="986"
					height="530" border="0">
					<tr>
						<td height="173"><img src="/img/empty.gif" alt="" width="1"
							height="173" border="0"><br></td>
					</tr>
					
					<tr>
						<td height="197" align="center"><a href="/"><img
								src="/img/empty.gif" alt="" width="220" height="197" border="0"></a><br></td>
					</tr>
					
					<tr>
						<td height="35"><img src="/img/empty.gif" alt="" width="1"
							height="35" border="0"><br></td>
					</tr>
					
					<tr>
						<td align="center" valign="top" class="b_area" width="320px" >
							<b>
								WELCOME TO <%= servlet.getHomeName().toUpperCase() %>!
							</b>
							
							<div align="center" valign="middle" width="320px" height="100px" valign="middle">
								<script async src="//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js">
								</script>
								<!-- gamez_2 -->
								<ins class="adsbygoogle"
									style="display:inline-block;width:320px;height:100px"
									data-ad-client="ca-pub-6386041731893209"
									data-ad-slot="2494900172">
								</ins>
								<script>
									(adsbygoogle = window.adsbygoogle || []).push({});
								</script>
							</div>
						</td>
					</tr>
				</table>
			</td>
			
			<td rowspan="3" background="/img/sm_right.jpg"
				style="background-repeat: no-repeat; background-position: 0% 0%;">
				&nbsp;</td>
		</tr>
		<tr>
			<td background="/img/bgv.jpg"
				style="background-repeat: repeat-y; background-position: 50% 0%;">
				<table cellpadding="0" cellspacing="0" align="center" height="100%"
					width="986" border="0">
					<tr valign="top">
						<jsp:include page="/includes/left.jsp" />
						
						<td class="b_area" width="526"
							style="padding-left: 12px; padding-right: 12px; padding-bottom: 12px;">

							<table cellpadding="0" cellspacing="0" align="center"
								width="100%" height="1" border="0">
								<tr>
									<td class="b_area" valign="top" align="left" height="35">
										• <b>Multiplayer Games</b> <br>• <b>In Game Chat</b> <br>•
										<b>Numerous Game Rooms</b> <br>• <b>Unlimited Free
											Play</b> <br>• <b>High Score Boards</b>
									</td>
									<td class="b_area" valign="top" align="left" height="35">
										• <b>Play Against the Computer</b> <br>• <b>Excellent
											Graphics</b> <br>• <b>No Broadband Connection Required</b> <br>•
										<b>Find your friends by email</b> <br>• <b>Top 100
											Best Players</b>
									</td>
								</tr>
							</table> <br> <br>
							<p>
								<a href="/billiards/"><img src="/img/gi_1.jpg" alt=""
									width="100" height="88" border="0" align="left"><br>
									<b>Billiards</b><br></a> Join the Worldwide Billiards
								Community and play free online billiards games with thousands
								users online from around the world. There are a variety of games
								played on billiards tables. Meet and play head-to-head the most
								popular of pocket games: GemaZer Pool, 8-ball billiards, 9-ball billiards,
								Straight Pool, Snooker, Pyramid and Carom or select the Bot
								Player to play against the computer. Invite your friends and
								chat with others all over the world! <br>Create your Group
								and play with your friends in your own room!
							</p>
							<p>
								<a href="/chess/"><img src="/img/gi_2.jpg" alt=""
									width="100" height="88" border="0" align="left"><br>
									<b>Chess</b><br></a> Chess is a board game and a mental sport
								for two players. It is played on a square board of 8 rows
								(called ranks) and 8 columns (called files), giving 64 squares
								of alternating colour, light and dark, with each player having a
								light square at his bottom right when facing the board. Each
								player begins the game with 16 pieces that each move and capture
								other pieces on the board in a unique way.
							</p>
							<p>
								<a href="/checkers/"><img src="/img/gi_3.jpg" alt=""
									width="100" height="88" border="0" align="left"><br>
									<b>Checkers</b><br></a> Checkers (Draughts) is a two-player
								game, where one player is assigned white checkers and the other
								black. The aim of the game is to capture the other player's
								checkers or make them impossible to move.
							</p> <br>
							<!--<p>
								<a href="/gladiator/"><img src="/img/gi_4.jpg" alt=""
									width="100" height="88" border="0" align="left"><br>
									<b>Gladiator</b><br></a> The most fun game is Gladiator!<br>
								As a gladiator your task is to fight and survive in the arena.
								There are two types of the game Classic and Expert.
							</p>-->


						</td>
						
						<jsp:include page="/includes/right.jsp" />
					</tr>
				</table>
			</td>
		</tr>
		
		<jsp:include page="/includes/footer.jsp" />
	</table>
</body>
</html>