<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@ page import="java.util.*, java.sql.*, gz.server.net.*, gz.server.net.Container.*, org.apache.commons.lang.*" %>

<%
String game = request.getParameter("g");
if (game == null)
	game = "billiards";

Container servlet = (Container) application.getAttribute(game);
if (servlet == null)
	return;

String gzid = request.getParameter("s");
if (gzid == null)
	return;

User user = servlet.getUserByGZID(gzid);
if (user == null)
	return;

List<Variant> variants = servlet.variants();

ArrayList<UserStats> stats = new ArrayList<UserStats>();

for (Variant variant: variants) {
	UserStats stat = user.getStats(variant);
	if (stat == null)
		return;
	
	stats.add(stat);
}

String registered;
Timestamp r = user.getRegistered();
if (r != null) {
	long t = r.getTime();
	Calendar calendar = Calendar.getInstance();
	calendar.setTimeInMillis(t);
	registered = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)) + "." + String.format("%02d", calendar.get(Calendar.MONTH) + 1) + "." + String.format("%04d", calendar.get(Calendar.YEAR));
} else
	registered = null;

String lastVisit;
r = user.getLastLogin();
if (r != null) {
	long t = r.getTime();
	Calendar calendar = Calendar.getInstance();
	calendar.setTimeInMillis(t);
	lastVisit = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)) + "." + String.format("%02d", calendar.get(Calendar.MONTH) + 1) + "." + String.format("%04d", calendar.get(Calendar.YEAR));
} else
	lastVisit = null;

String country = user.isShowingCountry() ? user.getCountry() : null;
if (country != null)
	country = country.toLowerCase();

Group group = user.getGroup();
String groupCreation = null;
if (group != null) {
	r = group.getCreated();
	if (r != null) {
		long t = r.getTime();
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(t);
		groupCreation = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)) + "." + String.format("%02d", calendar.get(Calendar.MONTH) + 1) + "." + String.format("%04d", calendar.get(Calendar.YEAR));
	}
}
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
		<title><%= servlet.getHomeName() %></title>
		<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
		<meta name="author" content="<%= servlet.getHomeName() %>">
		<meta name="abstract" content="<%= servlet.getDomainHost() %>">
		<meta name="resource-type" content="document">
		<meta name="url" content="<%= servlet.getHomeURL() %>">
		<meta name="robots" content="all">
		<link type="text/css" href="/cssp.css" rel="stylesheet">
		
		<script>
			<!--
			if(this!=top){
				top.location.href = "<%= servlet.getHomeURL() %>";
			}
			//-->
		</script>
	</head>
	
	<body bgcolor="#000000" marginheight="0" marginwidth="0" style="margin:0 0 0 0;">
		<table cellpadding="0" cellspacing="0" align="center" height="100%" width="586" border="0">
			<tr>
				<td height="205" align="center" valign="middle" background="/img/ppg_top.jpg" style="background-repeat:no-repeat; background-position:100% 0%;">
					<a href="<%= servlet.getHomeURL() %>">
						<img src="/img/empty.gif" alt="" width="180" height="205" border="0">
					</a>
					<br>
				</td>
			</tr>
		
			<tr>
				<td class="mf" style="padding:20px" align="left" valign="top" height="400" background="/img/ppg_bg.jpg" style="background-repeat:repeat-y;">
					<table bgcolor="#E0B571" cellpadding="0" cellspacing="0" align="center" width="100%" height="1" border="0">
						<tr>
							<td valign="top" align="left" class="mf">
								<table cellpadding="0" cellspacing="0" align="center" width="100%" border="0">
									<tr valign="top">
										<td width="120" align="left">
											<img class="imgb" src="/photobase/none.jpg" alt="" width="120" height="120" border="0" align="left">
										</td>
										
										<td width="10" align="left">
											<img src="/img/empty.gif" alt="" width="10" height="1" border="0">
										</td>
										
										<td width="90%" align="left" class="mf">
											<table class="mf" cellpadding="5" cellspacing="0" align="center" width="100%" border="0">
												<tr valign="middle">
													<% if (user.isSystem() || servlet.isAdminOfSomeGame(gzid)) { %>
													<td width="49">
														<object classid="clsid:d27cdb6e-ae6d-11cf-96b8-444553540000" codebase="http://fpdownload.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=8,0,0,0" width="49" height="40" id="iz_mdr" align="middle">
															<param name="allowScriptAccess" value="sameDomain" />
															<param name="movie" value="/swf/iz_mdr.swf" />
															<param name="menu" value="false" />
															<param name="quality" value="high" />
															<param name="bgcolor" value="#e0b571" />
															<embed src="/swf/iz_mdr.swf" menu="false" quality="high" bgcolor="#e0b571" width="49" height="40" name="iz_mdr" align="middle" allowScriptAccess="sameDomain" type="application/x-shockwave-flash" pluginspage="http://www.macromedia.com/go/getflashplayer" />
														</object>
													</td>
													<% } else { %>
													<td width="40">
														<img style="border:1px solid #A5864C" src="/img/avatars/a<%= user.getAvatar() %>.jpg" alt="" width="40" height="40" border="0">
													</td>
													<% } %>
													<td width="240" align="left">
														<b>
															User: &nbsp;&nbsp;
															<font style="color:#881D1E">
																<% if (country != null) { %>
																	<img src="/img/flags/<%= country %>.gif" alt="" width="18" height="12" border="0" align="middle">
																<% } %>
																&nbsp;<%= StringEscapeUtils.escapeHtml(user.getNick()) %>
															</font>
														</b>
														<br>
														<img src="/img/rating_<%= user.getStars() %>.gif" alt="" width="64" height="12" border="0" align="middle">
														<font style="color:#26750D; font-size:15px; font-weight:bold;">
															<%= (user.getRating() / 1000) %>
														</font>
													</td>
												</tr>
												
												<tr valign="middle">
													<% if (group != null && group.getOwner().equals(gzid)) { %>
													<td width="40">
														<a href="/groups/?<%= group.getID() %>">
															<img src="/img/zgics.jpg" title="<%= servlet.getHomeName() %> Group Owner 
Group: <%= group.getName() %>
Created: <%= groupCreation %>
Members: <%= group.getMemberCount() %>"
																alt="" width="40" height="40" border="0">
														</a>
													</td>
													<% } %>

													<td align="left" colspan="2">
														<b>
															Registered:  &nbsp;&nbsp;
															<font style="color:#881D1E">
																<%= registered != null ? registered : "" %>
															</font>
															<br>
															Last visit:  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
															<font style="color:#881D1E">
																<%= lastVisit != null ? lastVisit : "" %>
															</font>
														</b>
														<br>
														<br>
														<font class="sub">
															User ID: <%= gzid %>
														</font>
													</td>
												</tr>
											</table>
										</td>
									</tr>
								</table>
							</td>
						</tr>
						
						<tr>
							<td height="6">
								<img src="/img/empty.gif" alt="" width="1" height="6" border="0">
							</td>
						</tr>
						
						<tr>
							<td valign="top" align="center">
								<table class="mv" bgcolor="#E0B571" cellpadding="2" cellspacing="0" align="center" width="100%" border="1">
									<tr valign="middle">
										<td width="35%" align="center">
											Game Type
										</td>
										
										<td align="center">
											Games Played
										</td>
										
										<td align="center">
											Games Won
										</td>
										
										<td align="center">
											Rating Number
										</td>
									</tr>
									
									<% for (UserStats stat: stats) { %>
									<tr valign="middle">
										<td align="center">
											<b><%= stat.getVariant().getTitle() %></b>
										</td>
										
										<td align="center" style="color:#881D1E">
											<b><%= stat.getPlayeds() %></b>
										</td>
										
										<td align="center" style="color:#881D1E">
											<b><%= stat.getWins() %></b>
										</td>
										
										<td align="center" style="color:#26750D">
											<b><%= (stat.getRating() / 1000) %></b>
										</td>
									</tr>
									<% } %>
									
									<tr valign="middle">
										<td align="center" style="color:#881D1E">
											<b>TOTAL</b>
										</td>
										
										<td align="center" style="color:#881D1E">
											<b><%= user.getPlayeds() %></b>
										</td>
										
										<td align="center" style="color:#881D1E">
											<b><%= user.getWins() %></b>
										</td>
										
										<td align="center" style="color:#26750D">
											<b><%= (user.getRating() / 1000) %></b>
										</td>
									</tr>
								</table>
							</td>
						</tr>
						
						<tr>
							<td height="1" align="center" class="sub">
								<br>Other Statistics:<br>
								<a href="/user/?g=billiards&amp;s=<%= gzid %>">Billiards</a>
								<a href="/user/?g=chess&amp;s=<%= gzid %>">Chess</a>
								<a href="/user/?g=checkers&amp;s=<%= gzid %>">Checkers</a>
								<!--a href="/user/?g=gladiator&amp;s=<%= gzid %>">Gladiator</a>-->
							</td>
						</tr>
						
						<tr>
							<td>
								<div align="center" valign="middle" width="320px" height="100px" valign="middle">
									<br><br>
										<script async src="//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
										<!-- gamez_320x100 -->
										<ins class="adsbygoogle"
											 style="display:inline-block;width:320px;height:100px"
											 data-ad-client="ca-pub-6386041731893209"
											 data-ad-slot="8871544178"></ins>
										<script>
										(adsbygoogle = window.adsbygoogle || []).push({});
										</script>
								</div>
							</td>
						</tr>
					</table>
				</td>
			</tr>
			
			<tr>
				<td class="sub" align="center" valign="bottom" height="20" background="/img/ppg_bg.jpg" style="background-repeat:repeat-y;">
					&nbsp;
				</td>
			</tr>
			
			<tr>
				<td height="18">
					<img src="/img/ppg_bottom.jpg" alt="" width="586" height="18" border="0">
					<br>
				</td>
			</tr>
			
			<tr>
				<td height="100%">
					&nbsp;
				</td>
			</tr>
		</table>
	</body>
</html>