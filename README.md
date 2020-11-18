# Basic RTC Client

Basic RTC Client is an Android WebRTC client built on-top of [mediasoup-client-android](https://github.com/haiyangwu/mediasoup-client-android)

## Running

### Emulator

Before running the server make sure you edit `listenIps` inside `config.js` to use `127.0.0.1` as its IP. There is no need to set anything on the app. The app will automatically detect the emulator and use the proper loopback IP `10.0.2.2` to connect to the host.

### Android Device

Follow these steps before running the server and/or app
1. Make sure both the server machine and your android device are connected to the same network
2. Edit `listenIps` inside `config.js` to use the machine's IP address instead of `127.0.0.1`
3. Edit `API.java` inside the package `com.example.rtcclient.prefs` to match the same IP you set in `config.js` before (or you can set the server IP inside the app)

After you have setup networking you can run the server, app, and/or javascript client in any order. Javascript clients still run at `localhost:3000`

## HTTP Client

The default signaling implementation is a basic HTTP client that uses polling. Every second the client will poll for peers and automatically detect whether to add/remove peers, video-tracks, and/or audio-tracks


## Usage

You can implement your own signaling protocol (e.g. sockets) by implementing the interface `ISignalingStrategy` and then calling `changeSignalingStrategy`

```java
ISignalingStrategy socketClient = new SocketClient();
RoomClient roomClient = new RoomClient(socketClient, this);

...

// To seamlessly change the protocol
roomClient.changeSignalingStrategy(new HttpClient());
```
