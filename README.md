# icemir2

#### 介绍
基于网络冰雪传奇引擎流传代码进行修复，本程序用于学习研究使用，勿用于商业用途，若此产生的后果自行担负。

####兴趣爱好交流群：681622422

#### 软件架构
客户端采用安卓/IOS/H5 (此项目目前只提供安卓客户端)
服务端使用c++,需在linux下运行


#### 效果展示,以下都为真机运行

![输入图片说明](https://images.gitee.com/uploads/images/2022/0207/001529_6d6e0fb1_895094.png "1.png")
![输入图片说明](https://images.gitee.com/uploads/images/2022/0207/001604_90c20472_895094.png "2.png")
![输入图片说明](https://images.gitee.com/uploads/images/2022/0207/001652_6f99250c_895094.png "3.png")
![输入图片说明](https://images.gitee.com/uploads/images/2022/0207/001800_dc36aa56_895094.png "4.png")

#### 使用说明

1.  本程序为安卓通用客户端,请具备一定冰雪知识及安卓破解ip知识的人士使用

2.  下载后直接替换assets/game/js/gamelib.min.js的ip

3.  替换ip搜索"http://192.168.2.34:8087/"替换 "http://你自己的/" 保存即可

#### 更新记录

2022-02-05
1.  使用测试版本运行,删除各渠道发行多余代码

2.  新增安卓与js通信

3.  新增热更新(使用下面详细说明)

4.  新增多线程断点续传下载

5.  新增zip包解压功能

6.  新增热更新等待界面(未画完整UI,后面加上话面及热更进度提示)

#### 关于wwwroot网站中的配置变更
1.   服务器列表文件由GetServerList.php修改为ServerList.json, 内容保留以前的即可

2.   公告文件修改为: Notice.json,内容请保持原来的一致

#### 关于热更新使用说明
1.   在当前点击使用version.bat ,运行完成后会在assets项目下生成文件: project.manifest,version.manifest

2.   将assets下包含game,project.manifest,version.manifest 等整个放入到服务器wwwroot网站中

3.   如果使用热更新请修改project.manifest,version.manifest对应的版本号,安卓启动时会自动检测变更文件并更新

4.   使用上有什么问题请到群里沟通
