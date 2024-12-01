<%@ page language="java" contentType="text/html; charset=utf-8"
	pageEncoding="utf-8"%>
<%@ page
	import="java.util.*, java.sql.*, gz.server.net.*, gz.server.net.Container.*, org.apache.commons.lang.*"%>
<%
	String game = request.getParameter("g");
	if (game == null)
		game = "billiards";
	
	Container servlet = (Container) application.getAttribute(game);
	if (servlet == null)
		return;
	
	String variantName = request.getParameter("v");
	Variant variant;
	if (variantName == null)
		variant = servlet.variants().get(0);
	else
		variant = servlet.getVariant(variantName);

	List<User> users = servlet.getTop100(variant);
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
		<title><%= servlet.getHomeName() %> | <%= servlet.getTitle() %> Top 100</title>
		<meta name="description"
			content="<%= servlet.getHomeName() %> : Top 100 Best <%= variant.getTitle() %> Players">
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

			function openwin_p(adr) {
				var gametype = '<%= servlet.getGameName() %>';
				m = window.open('/user/?g=' + gametype + '&s=' + adr, 'ustat');
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
					style="background-repeat: no-repeat; background-position: 100% -150px;">
					&nbsp;
				</td>

				<td valign="top" width="986" height="380"
					background="/img/gzmain_base.jpg"
					style="background-repeat: no-repeat; background-position: 0px -150px;">
					<table cellpadding="0" cellspacing="0" align="center" width="986"
						height="380" border="0">
						<tr>
							<td height="23">
								<img src="/img/empty.gif" alt="" width="1"
									height="23" border="0">
							</td>
						</tr>
						
						<tr>
							<td height="197" align="center">
								<a
								href="<%=servlet.getHomeURL()%>">
									<img src="/img/empty.gif"
										alt="" width="220" height="197" border="0">
								</a>
							</td>
						</tr>
						
						<tr>
							<td height="35">
								<img src="/img/empty.gif" alt="" width="1"
									height="35" border="0">
							</td>
						</tr>

						<tr>
							<td align="center" valign="top" class="b_area" width="320px">
								<b>
									TOP 100 BEST <%= variant.getTitle().toUpperCase() %> PLAYERS
								</b>
								
								<div align="center" valign="middle" width="320px" height="100px" valign="middle">
									<script async src="//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
									<!-- gamez_320x100_2 -->
									<ins class="adsbygoogle"
										 style="display:inline-block;width:320px;height:100px"
										 data-ad-client="ca-pub-6386041731893209"
										 data-ad-slot="1348277370"></ins>
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

							<td align="center" class="b_area" width="526" style="padding-left: 12px; padding-right: 12px; padding-bottom: 12px;">
								<table class="b_area" cellpadding="2" cellspacing="0"
									align="center" width="100%" border="0">
									<tr valign="middle">
										<td align="center" style="border: 1px solid #FBF4C6;"><b>#</b>
										</td>

										<td align="center"
											style="border-right: 1px solid #FBF4C6; border-bottom: 1px solid #FBF4C6; border-top: 1px solid #FBF4C6;">
											<b>Player</b>
										</td>

										<td align="center"
											style="border-right: 1px solid #FBF4C6; border-bottom: 1px solid #FBF4C6; border-top: 1px solid #FBF4C6;">
											<b>Rating</b>
										</td>

										<td align="center"
											style="border-right: 1px solid #FBF4C6; border-bottom: 1px solid #FBF4C6; border-top: 1px solid #FBF4C6;">
											<b>Games<br>Played
										</b>
										</td>

										<td align="center"
											style="border-right: 1px solid #FBF4C6; border-bottom: 1px solid #FBF4C6; border-top: 1px solid #FBF4C6;">
											<b>Games<br>Won
										</b>
										</td>
									</tr>

									<%
										for (int i = 0; i < users.size(); i++) {
											User user = users.get(i);
											String gzid = user.getGZID();
											UserStats stats = user.getStats(variant);
											String country = user.isShowingCountry() ? user.getCountry() : null;
											if (country != null)
												country = country.toLowerCase();
									%>
									<tr valign="middle">
										<td align="center"
											style="border-left: 1px solid #FBF4C6; border-right: 1px solid #FBF4C6; border-bottom: 1px solid #FBF4C6;">
											<font style="color: #FEB21F"> <b><%=(i + 1)%></b>
										</font>
										</td>

										<td class="mtt_area" align="left" valign="middle"
											style="border-right: 1px solid #FBF4C6; border-bottom: 1px solid #FBF4C6;">
											<a onmouseover="status='View Statistics'; return true"
											onmouseout="status=''; return true"
											href="javascript:openwin_p('<%=gzid%>')"> 
											<% if (country != null) { %> 
											<img src="/img/flags/<%=country%>.gif" alt="" width="18" height="12"
											border="0" valign="middle" > 
											<% } %>
											&nbsp; <%= StringEscapeUtils.escapeHtml(user.getNick()) %>
										</a>
										</td>

										<td align="center"
											style="border-right: 1px solid #FBF4C6; border-bottom: 1px solid #FBF4C6;">
											&nbsp; <font
											style="color: #26750D; font-size: 15px; font-weight: bold;">
												<%=(stats.getRating() / 1000)%>
										</font>
										</td>

										<td align="center"
											style="border-right: 1px solid #FBF4C6; border-bottom: 1px solid #FBF4C6;">
											<%=stats.getPlayeds()%>
										</td>

										<td align="center"
											style="border-right: 1px solid #FBF4C6; border-bottom: 1px solid #FBF4C6;">
											<%=stats.getWins()%>
										</td>
									</tr>
									<%
										}
									%>
								</table>
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