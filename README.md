# Android Ping 1.0
Ping in Android from your code.
1. Does NOT invoke CLI ping command.
2. Support for IPv4 and IPv6.

Implementation notes:
1. Listener is executed on calling Thread (Not UI Thread).
2. Requires Lollipop or greater.
3. As always, don't do I/O on the UI Thread.

## Sample Usage
See the MainActivity for more.
```Java
final InetAddress dest = InetAddress.getByName(mHost);
final Ping ping = new Ping(dest, new Ping.PingListener() {
    @Override
    public void onPing(final long timeMs, final int count) {
        Log.d(TAG, "#" + count + " ms: " + timeMs + " ip: " + dest.getHostAddress(), null);
    }

    @Override
    public void onPingException(final Exception e, final int count) {
        Log.e(TAG, "#" + count  + " ip: " + dest.getHostAddress(), e);
    }

});

AyscTask.THREAD_POOL_EXECUTOR.execute(ping);
```

## Bind to android.net.Network
You can bind the ping to a Network (e.g. WiFi or Mobile).   See MainActivity for more.
```Java
final Ping ping = new Ping(inetAddress, listener);
ping.setNetwork(network);
AyscTask.THREAD_POOL_EXECUTOR.execute(ping);
```
## Build just the library (aar)

    gradlew lib:assembleRelease
