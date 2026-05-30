// 则默认使用英语
var defaultLanguage = "en";

// 底部按钮是否可见
var bottomBtnIsVisible = true;

var answerBottomBtnHtml = '<div id="navigation-buttons" class="navigation-buttons-div">'
	+'<button id="back-button" class="navigation-buttons-button" onclick="goBack()">'
		+'<img class="navigation-buttons-button-img" src="../public-images/left-select.png">'
	+'</button>'
	+'<button id="forward-button" class="navigation-buttons-button" disabled>'
		+'<img class="navigation-buttons-button-img" src="../public-images/right-normal.png">'
	+'</button></div>';

// 获取URL中的参数值
function getParameterByName(name) {
	var url = window.location.href;
	name = name.replace(/[\[\]]/g, "\\$&");
	var regex = new RegExp("[?&]" + name + "(=([^&#]*)|&|#|$)"),
	results = regex.exec(url);
	if (!results) return null;
	if (!results[2]) return '';
	return decodeURIComponent(results[2].replace(/\+/g, " "));
}

// 返回上一页
function goBack() {
	window.history.back();
}

// 前进到下一页
function goForward() {
	window.history.forward();
} 