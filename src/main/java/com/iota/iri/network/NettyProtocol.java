package com.iota.iri.network;

import io.netty.channel.ChannelHandler;

public interface NettyProtocol {
    ChannelHandler[] getServerChannelHandlers();
    ChannelHandler[] getClientChannelHandlers();
}
