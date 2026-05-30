访问对应页面的时候，需要传入4个参数：

1. language
	国家语言简称，现阶段取值：
	中文简体：zh-CN
	英文：en
	日语：ja
	法语：fr
	德语：de
	韩语：ko
	意大利语：it
	葡萄牙语：pt
	西班牙语：es
	中文繁体：zh-TW
	俄语：ru
	越南语:vi
	泰国语:th
	
	如果缺少该字段，或者该字段为不支持的语言，默认显示英文。
	
2. dev_name
	设备类型的名称，取值如下：
	CoolLED536, CoolLED, CoolLEDX, CoolLEDS, CoolLEDM, CoolLEDU
	
3. height
	显示屏分辨率的高度。
	
4. width
	显示屏分辨率的宽度。
	
5. pl
	当前平台，取值：ios, android, pc

6. appv
	应用当前版本号，示例：
	1.2.1
	
示例：
	比如现在设备为CoolLEDX 16x64，访问帮助页面：
	path/apphtml/help/index.html?language=en&dev_name=CoolLEDX&height=16&width=64&pl=ios&appv=1.2.1

