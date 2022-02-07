// 执行命令 Node.JS
// node version_generator.js -v 1.0.0 -u http://your-server-address/tutorial-hot-update/remote-assets/ -s native/package/ -d assets/
// node version_generator.js -v 1.0.0 -u https://brain-exercise.oss-cn-beijing.aliyuncs.com/hot-update/remote-assets/ -s ./ -d ./
// (version_generator.js 被放在了 jsb-default 目录下)
// -v 指定 Manifest 文件的主版本号。
// -u 指定服务器远程包的地址，这个地址需要和最初发布版本中 Manifest 文件的远程包地址一致，否则无法检测到更新。
// -s 本地原生打包版本的目录相对路径。
// -d 保存 Manifest 文件的地址。

var fs = require('fs');
var path = require('path');
var crypto = require('crypto');

var manifest = {
    // packageUrl: 'http://localhost/tutorial-hot-update/remote-assets/',
    // remoteManifestUrl: 'http://localhost/tutorial-hot-update/remote-assets/project.manifest',
    // remoteVersionUrl: 'http://localhost/tutorial-hot-update/remote-assets/version.manifest',
    current_version: '1.0.6',
    assets: {},
    searchPaths: []
};

var dest = 'assets';
var src = 'assets';

// Parse arguments
var i = 2;
while (i < process.argv.length) {
    var arg = process.argv[i];

    switch (arg) {
        case '--url':
        case '-u':
            // var url = process.argv[i + 1];
            // manifest.packageUrl = url;
            // manifest.remoteManifestUrl = url + 'project.manifest';
            // manifest.remoteVersionUrl = url + 'version.manifest';
            i += 2;
            break;
        case '--version':
        case '-v':
            manifest.current_version = process.argv[i + 1];
            i += 2;
            break;
        case '--src':
        case '-s':
            // src = process.argv[i + 1];
            i += 2;
            break;
        case '--dest':
        case '-d':
            // dest = process.argv[i + 1];
            i += 2;
            break;
        default:
            i++;
            break;
    }
}


function readDir(dir, obj) {
    var stat = fs.statSync(dir);
    if (!stat.isDirectory()) {
        return;
    }
    var subpaths = fs.readdirSync(dir), subpath, size, md5, compressed, relative;
    for (var i = 0; i < subpaths.length; ++i) {
        if (subpaths[i][0] === '.') {
            continue;
        }
        subpath = path.join(dir, subpaths[i]);
        stat = fs.statSync(subpath);
        if (stat.isDirectory()) {
            readDir(subpath, obj);
        }
        else if (stat.isFile()) {
            // Size in Bytes
            size = stat['size'];
            md5 = crypto.createHash('md5').update(fs.readFileSync(subpath, 'binary')).digest('hex');
            compressed = path.extname(subpath).toLowerCase() === '.zip';

            relative = path.relative(src, subpath);
            relative = relative.replace(/\\/g, '/');
            relative = encodeURI(relative);

            // relative = relative.replace('../../GameApp2', 'game/resource/assets');

            obj[relative] = {
                'size': size,
                'md5': md5
            };
            if (compressed) {
                obj[relative].compressed = true;
            }

            console.log(relative);
        }
    }
}

var mkdirSync = function (path) {
    try {
        fs.mkdirSync(path);
    } catch (e) {
        if (e.code != 'EEXIST') throw e;
    }
}

// Iterate res and src folder
readDir(path.join(src, 'game'), manifest.assets);
// readDir(dataPath, manifest.assets);
// readDir(resPath, manifest.assets);

var destManifest = path.join(dest, 'project.manifest');
var destVersion = path.join(dest, 'version.manifest');

mkdirSync(dest);

fs.writeFile(destManifest, JSON.stringify(manifest), (err) => {
    if (err) throw err;
    console.log('Manifest successfully generated');
});

delete manifest.assets;
delete manifest.searchPaths;
fs.writeFile(destVersion, JSON.stringify(manifest), (err) => {
    if (err) throw err;
    console.log('Version successfully generated');
});
