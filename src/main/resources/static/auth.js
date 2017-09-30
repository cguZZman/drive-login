var w;
$(function() {
	$("#login-code").focus();
	$("#login-code").keypress(function(event) {
		var keyCode = (event.keyCode ? event.keyCode : event.which);
		if (keyCode == 13) {
			$('#beginButton').trigger('click');
		}
	});
	$("#url").keypress(function(event) {
		var keyCode = (event.keyCode ? event.keyCode : event.which);
		if (keyCode == 13) {
			$('#finishButton').trigger('click');
		}
	});
});
jQuery.extend({
	getQueryParameters : function(str) {
		str = (str || document.location.search);
		return str.substring(str.indexOf('?') + 1).split("&").map(function(n) {
			return n = n.split("="), this[n[0]] = n[1], this
		}.bind({}))[0];
	}
});
function begin(prefix) {
	$("#errorStep1").hide();
	$("#errorStep1gen").hide();
	$("#login-code").val($("#login-code").val().toUpperCase());
	$.ajax({
		url : prefix + "service.jsp",
		data : {
			action : 'begin',
			pin : $("#login-code").val().toUpperCase()
		},
		success : function(data) {
			if (data.success) {
				$("#version").val(data.version);
				$("#step1").hide();
				$("#step2").show();
				$("#login-button").focus();
			} else {
				$("#errorStep1").show();
			}
		},
		error : function(data){
			$("#errorStep1gen").show();
			$("#backButton").focus();
		}
	});
}

function login() {
	var loginDataMap = {
		"v1" : {
			url : 'https://login.live.com/oauth20_authorize.srf',
			clientId : '0000000048145120',
			scope : 'wl.offline_access wl.skydrive',
			redirectUri : 'https://login.live.com/oauth20_desktop.srf'
		},
		"v2" : {
			url : 'https://login.microsoftonline.com/common/oauth2/v2.0/authorize',
			clientId : '8913898c-1b24-4c09-a5d4-1d19d35690ea',
			scope : 'offline_access sites.read.all files.read.all user.read',
			redirectUri : 'https://login.microsoftonline.com/common/oauth2/nativeclient'
		}
	};
	loginData = loginDataMap["v"+$("#version").val()]
	params = {
		client_id : loginData.clientId,
		scope : loginData.scope,
		response_type : 'code',
		redirect_uri : loginData.redirectUri,
		display : 'touch',
		state : $("#login-code").val()
	}
	var left = ($(window).width() / 2) - (600 / 2);
	var top = ($(window).height() / 2) - (600 / 2);
	w = window.open(loginData.url + '?' + $.param(params), '_blank',
			'width=600,height=600,menubar=no,status=yes,top=' + top + ',left='
					+ left);
	$("#url").focus();
}

function finish(prefix) {
	$("#errorStep2").hide();
	$("#errorStep2-server").hide();
	$("#errorStep2-server-gen").hide();
	try {
		w.close();
	} catch (e) {
	}
	$('#finishButton').prop('disabled', true);
	$("#url").prop('disabled', true);
	var url = $("#url").val();
	var queryParams = $.getQueryParameters(url);
	if (queryParams.code && queryParams.state
			&& queryParams.state == $("#login-code").val()) {
		$.ajax({
			url : prefix + "service.jsp",
			data : {
				action : 'login',
				code : queryParams.code,
				pin : $("#login-code").val()
			},
			success : function(data) {
				if (data.success) {
					$("#step2").hide();
					$("#step3").show();
				} else {
					$("#errorStep2-server").show();
				}
				$("#backButton").focus();
			},
			error : function(data){
				$("#errorStep2-server-gen").show();
				$("#backButton").focus();
			}
		});
	} else {
		$("#errorStep2").show();
		$("#finishButton").prop('disabled', false);
		$("#url").prop('disabled', false);
	}

}

function backHome() {
	$("#login-code").val("");
	$("#url").prop('disabled', false);
	$("#url").val("");
	$('#finishButton').prop('disabled', false);
	location.reload(true);
}