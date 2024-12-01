<%@ page language="java" contentType="text/html; charset=iso-8859-1"
	pageEncoding="iso-8859-1"%>
<%@ page import="gz.server.net.*"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<%!
String getLocaleLanguage(PageContext pageContext) {
	String lang = (String) pageContext.getAttribute("localeCode");
	if (lang == null)
			return "en";
	
	int p = lang.indexOf("-");
	if (p != -1)
		lang = lang.substring(0, p);
	
	return lang.toLowerCase();
}

String getCountry(PageContext pageContext) {
	String country = (String) pageContext.getAttribute("country");
	if (country == null)
			return "US";
	
	int p = country.indexOf("-");
	if (p != -1)
		country = country.substring(0, p);
	
	return country.toUpperCase();
}
%>

<%
Container servlet = (Container) application.getAttribute("chess");
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
String forumurl = servlet.getForumURL();
String serverurl = servlet.getServerURL();
String swfdir = servlet.getSWFDir();

final boolean useDFP = false;

pageContext.setAttribute("localeCode", request.getLocale().toLanguageTag());
pageContext.setAttribute("country", request.getLocale().getCountry());

String fsid = null;
Cookie[] cookies = request.getCookies();
if (cookies != null)
	for (Cookie cookie: cookies)
		if (cookie.getName().equals("fsid")) {
			fsid = cookie.getValue();
			break;
		}
%>

<!DOCTYPE html>

<html>
	<head>
		<title><%= servlet.getHomeName() %> | <%= servlet.getTitle() %> Online Game
		</title>
		<meta name="title" content="<%= servlet.getHomeName() %> - Online Multiplayer Games">
		<meta name="description" content="Chess. Multiplayer Online games.">
		<meta name="keywords" content="Chess. Multiplayer game">
		<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">
		<meta name="author" content="<%= servlet.getHomeName() %>">
		<meta name="abstract" content="<%= servlet.getDomainHost() %>">
		<meta name="resource-type" content="document">
		<meta name="robots" content="all">
		<link rel="icon" href="/favicon.ico" type="image/x-icon">
		<script>
		<!--
		var mset = 0;
		var smj = "";
		if(this!=top){
			top.location.href = "";
		}
		
		function init_m(){
			if (mset)
				return;

			mset = 1;
			var vp = 'setlang=<%= getLocaleLanguage(pageContext) %>&setcountry=<%= getCountry(pageContext) %>&<%= fsid != null ? "s=" + fsid + "&" : "" %>z=chess&homeversion=<%= homeversion %>&gameversion=<%= gameversion %>&smenuversion=<%= smenuversion %>&domainhost=<%= domainhost %>&homename=<%= homename %>&homeurl=' + encodeURI('<%= homeurl %>') + '&contenturl=' + encodeURI('<%= contenturl %>') + '&forumurl=' + encodeURI('<%= forumurl %>') + '&serverurl=' + encodeURI('<%= serverurl %>') + '&swfdir=' + encodeURI('<%= swfdir %>');
			if (document.all){
				smj = document.all.ins_flash;
			}else if (document.getElementById){  
				smj = document.getElementById("ins_flash");
			}else{  
				smj = document.ins_flash;  
			}
			var STV = '<%= STV %>';
				smj.innerHTML = '<object classid="clsid:d27cdb6e-ae6d-11cf-96b8-444553540000" codebase="http://fpdownload.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=9,0,115,0" width="1000" height="500" id="flashindex" align="middle">'
						+ '<param name="allowScriptAccess" value="always" />'
						+ '<param name="menu" value="false" />'
						+ '<param name="flashvars" value="'+vp+'" />'
						+ '<param name="movie" value="/swf_v5/base/flashindex_'+STV+'.swf" />'
						+ '<param name="quality" value="high" />'
						+ '<param name="bgcolor" value="#000000" />'
						+ '<embed src="<%= homeurl %>/swf_v5/base/flashindex_'+STV+'.swf" flashvars="'+vp+'" menu="false" quality="high" bgcolor="#000000" width="1000" height="500" name="flashindex" align="middle" allowScriptAccess="always" type="application/x-shockwave-flash" pluginspage="http://www.macromedia.com/go/getflashplayer" />'
						+ '</object>';
						
			<% if (useDFP) { %>
			defineSlots();
			<% } %>
		}
		//-->
		</script>
		
		<script> 
		function toggle() {
			var ele = document.getElementById("toggleText");
			var text = document.getElementById("displayText");
			if(ele.style.display == "block") {
					ele.style.display = "none";
			}
			else {
				ele.style.display = "block";
			}
		} 
		</script>

		<style>
		body {
			margin: 0 0 0 0;
			color: #ffffff;
			background-color: #000000;
			font-family: verdana, arial, sans-serif;
			font-size: 11px;
		}

		a:not(#displayText) {
			color: #ffffff;
			font-family: verdana, arial, sans-serif;
			font-size: 11px;
			text-decoration: underline;
		}

		a:hover:not(#displayText) {
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
		
		<script async='async' src='https://www.googletagservices.com/tag/js/gpt.js'></script>
		
		<script>
			var googletag = googletag || {};
			googletag.cmd = googletag.cmd || [];
		</script>

		<% if (useDFP) { %>
		<script>
			var slotsDefined = 0;
			function defineSlots() {
				if (slotsDefined)
					return;
				
				slotsDefined = 1;
				googletag.cmd.push(function() {
					googletag.pubads().enableAsyncRendering();
					
					googletag.defineSlot('/137571108/gamez_chess_header', [728, 90], 'div-gpt-ad-1477637113781-0').addService(googletag.pubads());
			
					googletag.defineSlot('/137571108/gamez_chess_left', [160, 600], 'div-gpt-ad-1477637175508-0').addService(googletag.pubads());
			
					googletag.defineSlot('/137571108/gamez_chess_right', [160, 600], 'div-gpt-ad-1477637221269-0').addService(googletag.pubads());
					
					googletag.pubads().disableInitialLoad();
					googletag.enableServices();
					
					googletag.cmd.push(function() { googletag.display('div-gpt-ad-1477637113781-0'); });
					
					googletag.cmd.push(function() { googletag.display('div-gpt-ad-1477637175508-0'); });
					
					googletag.cmd.push(function() { googletag.display('div-gpt-ad-1477637221269-0'); });
			
					setTimeout(function(){googletag.pubads().refresh();}, 1000);
					setInterval(function(){googletag.pubads().refresh();}, 30 * 60 * 1000);
				});
			}
		</script>
		<% } %>
	</head>

	<body onload="init_m()">
		<div id="fb-root"></div>
		<script>(function(d, s, id) {
			var js, fjs = d.getElementsByTagName(s)[0];
			if (d.getElementById(id)) return;
			js = d.createElement(s); js.id = id;
			js.src = "//connect.facebook.net/pt_BR/sdk.js#xfbml=1&version=v2.8";
			fjs.parentNode.insertBefore(js, fjs);
			}(document, 'script', 'facebook-jssdk'));
		</script>
		
		<div align="center">
			<table cellpadding="0" cellspacing="0" align="center" width="100%"
				height="100%" border="0">
				<tr align="center" valign="middle" colspan="20" width="100%">
					<td valign="middle" align="left" colspan="5" >
						<% if (!domainhost.equals("localhost")) { %>
						<% if (useDFP) { %>
						<!-- /137571108/gamez_chess_left -->
						<div id='div-gpt-ad-1477637175508-0' style='height:600px; width:160px;'>
						</div>
						<% } else { %>
						<script async src="//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
						<!-- gamez_160x600 -->
						<ins class="adsbygoogle"
							 style="display:inline-block;width:160px;height:600px"
							 data-ad-client="ca-pub-6386041731893209"
							 data-ad-slot="5051950176"></ins>
						<script>
						(adsbygoogle = window.adsbygoogle || []).push({});
						</script>
						<% } %>
						<% } %>
					</td>
					
					<td align="center" valign="middle" colspan="15">
						<table cellpadding="0" cellspacing="0" align="center" width="1000" height="100%" border="0">
							<tr align="center" valign="top" colspan="15" width="100%">
								<td valign="top" colspan="15" align="center">
									<span> 
										<b> 
											<c:choose>
												<c:when test="${localeCode == 'pt'}">
													<c:import var="dev" url="include/dev/header/pt.jsp" />
													<c:out value="${dev}" escapeXml="false" />
												</c:when>
												<c:when test="${localeCode == 'pt-BR'}">
													<c:import var="dev" url="include/dev/header/pt.jsp" />
													<c:out value="${dev}" escapeXml="false" />
												</c:when>
												<c:when test="${localeCode == 'es'}">
													<c:import var="dev" url="include/dev/header/es.jsp" />
													<c:out value="${dev}" escapeXml="false" />
												</c:when>
												<c:otherwise>
													<c:import var="dev" url="include/dev/header/en.jsp" />
													<c:out value="${dev}" escapeXml="false" />
												</c:otherwise>
											</c:choose>
										</b>
									</span>
									<br> <br> <br>
								</td>
							</tr>
							
							<tr align="center" valign="top" colspan="15" width="100%">
								<td valign="middle" align="center" colspan="15" width="100%" >
									<% if (!domainhost.equals("localhost")) { %>
									<% if (useDFP) { %>
									<!-- /137571108/gamez_chess_header -->
									<div id='div-gpt-ad-1477637113781-0' style='height:90px; width:728px;'>
									</div>
									<% } else { %>
									<script async src="//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
									<!-- atrativa.net.br -->
									<ins class="adsbygoogle"
										 style="display:inline-block;width:728px;height:90px"
										 data-ad-client="ca-pub-6386041731893209"
										 data-ad-slot="2414174979"></ins>
									<script>
									(adsbygoogle = window.adsbygoogle || []).push({});
									</script>
									<% } %>
									<% } %>
								</td>
							</tr>
							
							<tr valign="middle" align="center" colspan="15">
								<td valign="middle" align="center" colspan="15" id="ins_flash" width="1000" height="500">
									<noscript>
										Please enable javascript!
									</noscript>
								</td>
							</tr>
							
							<tr valign="bottom" width="1000" colspan="15" >
								<td valign="middle" colspan="15" class="bc" align="center">
									&nbsp;
								</td>
							</tr>
							
							<tr valign="bottom" colspan="15">
								<td colspan="15" class="bc" align="center"><br> <br> <a
									href="<%=servlet.getHomeURL()%>"><b><%=servlet.getHomeName()%></b></a>
									&nbsp; &#8226; &nbsp; <a href="/billiards/"><b>Billiards</b></a>
									&nbsp; &#8226; &nbsp; <a href="/checkers/"><b>Checkers</b></a> &nbsp;
									<!--&#8226; &nbsp; <a href="/gladiator/"><b>Gladiator</b></a> &nbsp;-->
									&#8226; &nbsp; <a href="/faq" target="_blank"><b>FAQ</b></a>
									&#8226; &nbsp; <a href="<%= forumurl %>" target="_blank"><b>Support</b></a>
								</td>
							</tr>
							
							<tr valign="bottom" width="1000" colspan="15" >
								<td valign="middle" colspan="15" class="bc" align="center">
									&nbsp;
								</td>
							</tr>
							
							<tr valign="bottom" width="1000" colspan="15" >
								<td valign="middle" width="100%" colspan="15" class="bc" align="center">
									&nbsp;
								</td>
							</tr>			
							
							<tr align="center" valign="bottom" colspan="15" width="1000" >
								<td align="center" valign="middle" colspan="4" width="20%">
									<form action="https://www.paypal.com/cgi-bin/webscr"
										method="post" target="_top">
										<input type="hidden" name="cmd" value="_s-xclick"> <input
											type="hidden" name="hosted_button_id" value="65F9GT5F44SA6">
										<input type="image"
											src="https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif"
											border="0" name="submit"
											alt="PayPal - The safer, easier way to pay online!"> <img
											alt="" border="0"
											src="https://www.paypalobjects.com/pt_BR/i/scr/pixel.gif"
											width="1" height="1">
									</form>
								</td>

								<td align="center" valign="middle" colspan="3" width="20%">
									<div class="fb-page"
										data-href="https://www.facebook.com/gamezv5/"
										data-tabs="timeline" data-width="180" data-height="70"
										data-small-header="true" data-adapt-container-width="true"
										data-hide-cover="true" data-show-facepile="true">
										<blockquote cite="https://www.facebook.com/gamezv5/"
											class="fb-xfbml-parse-ignore">
											<a href="https://www.facebook.com/gamezv5/">GameZ</a>
										</blockquote>
									</div>
								</td>
								
								<td align="center" valign="middle" colspan="4" width="20%">
									<script src="http://doebitcoin.com.br/botao/coin.js"></script>
									<script>
										Doe.go({
											wallet_address: "1JPEWvoxF3x1Am1dui5c3CptXQp8iJXhKX"
											, currency: "bitcoin"
											, counter: "count"
											, alignment: "al"
											, qrcode: true
											, auto_show: false
											, lbl_button: "Donate Bitcoin"
											, lbl_address: "Our Bitcoin address:"
											, lbl_count: "donations"
											, lbl_amount: "BTC"
										});
									</script>
								</td>

								<td align="center" valign="middle" colspan="3" width="20%">
									<a href="https://twitter.com/gamez_v5"
										class="twitter-follow-button" data-show-count="false">Follow
										@gamez_v5
									</a>
									<script async='async' src="//platform.twitter.com/widgets.js" charset="utf-8" type="text/javascript"></script>
								</td>

								<td align="center" valign="middle" colspan="4" width="20%">
									<form
										action="https://pagseguro.uol.com.br/checkout/v2/donation.html"
										method="post">
										<input type="hidden" name="currency" value="BRL" /> <input
											type="hidden" name="receiverEmail"
											value="misterioso_matematico@hotmail.com" /> <input
											type="hidden" name="iot" value="button" /> <input
											type="image"
											src="https://stc.pagseguro.uol.com.br/public/img/botoes/doacoes/209x48-doar-assina.gif"
											name="submit"
											alt="Pague com PagSeguro - é rápido, grátis e seguro!" />
									</form>
								</td>
							</tr>
							
							<tr valign="bottom" width="1000" colspan="15" >
								<td valign="middle" colspan="15" class="bc" align="center">
									&nbsp;
								</td>
							</tr>
							
							<tr>
								<td valign="top" colspan="15" align="center">
									<span> 
										<b> 
											<c:choose>
												<c:when test="${localeCode == 'pt'}">
													<c:import var="dev" url="include/dev/footer/pt.jsp" />
													<c:out value="${dev}" escapeXml="false" />
												</c:when>
												<c:when test="${localeCode == 'pt-BR'}">
													<c:import var="dev" url="include/dev/footer/pt.jsp" />
													<c:out value="${dev}" escapeXml="false" />
												</c:when>
												<c:when test="${localeCode == 'es'}">
													<c:import var="dev" url="include/dev/footer/es.jsp" />
													<c:out value="${dev}" escapeXml="false" />
												</c:when>
												<c:otherwise>
													<c:import var="dev" url="include/dev/footer/en.jsp" />
													<c:out value="${dev}" escapeXml="false" />
												</c:otherwise>
											</c:choose>
										</b>
									</span>
								</td>
							</tr>
						</table>
					</td>
					
					<td valign="middle" align="right" colspan="5" >
						<% if (!domainhost.equals("localhost")) { %>
						<% if (useDFP) { %>
						<!-- /137571108/gamez_chess_right -->
						<div id='div-gpt-ad-1477637221269-0' style='height:600px; width:160px;'>
						</div>
						<% } else { %>
						<script async src="//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
						<!-- gamez_160x600_2 -->
						<ins class="adsbygoogle"
							 style="display:inline-block;width:160px;height:600px"
							 data-ad-client="ca-pub-6386041731893209"
							 data-ad-slot="1400479777"></ins>
						<script>
						(adsbygoogle = window.adsbygoogle || []).push({});
						</script>
						<% } %>
						<% } %>
					</td>
				</tr>
				
				<tr valign="bottom" width="100%" >
					<td valign="middle" align="left" colspan="5" >
					</td>
					
					<td valign="middle" colspan="15" align="center" width="100%" >
						<table cellpadding="0" cellspacing="0" align="center" border="0">
							<tr>
								<td class="bc" valign="middle" align="left"><br> &nbsp;
									&#8226; &nbsp; <a target="_blank" href="/top?g=chess">Chess
										Top 100</a> &nbsp; &nbsp; &nbsp; &#8226; &nbsp; <a target="_blank"
									href="/rules?g=chess">Chess Rules</a><br>
								<br></td>
							</tr>
						</table>
					</td>
					
					<td valign="middle" align="right" colspan="5" >
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