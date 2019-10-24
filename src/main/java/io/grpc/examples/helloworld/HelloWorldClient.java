/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.examples.helloworld;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;

/**
 * A simple client that requests a greeting from the {@link HelloWorldServer}.
 */
public class HelloWorldClient {
  private static final Logger logger = Logger.getLogger(HelloWorldClient.class.getName());

  private final Channel channel;
  private final ManagedChannel managedChannel;
  private final GreeterGrpc.GreeterBlockingStub blockingStub;
  private final HeaderClientInterceptor interceptor;

  /** Construct client connecting to HelloWorld server at {@code host:port}. */
  public HelloWorldClient(String target, SslContext sslContext) {
    this(NettyChannelBuilder.forTarget(target)
        .usePlaintext()
                        //.overrideAuthority("foo.test.google.fr")  /* Only for using provided test certs. */
//                                        .sslContext(sslContext)
        .build());
  }

  /** Construct client for accessing HelloWorld server using the existing channel. */
  HelloWorldClient(ManagedChannel originChannel) {
    managedChannel = originChannel;
    interceptor = new HeaderClientInterceptor();
    channel = ClientInterceptors.intercept(originChannel, interceptor);
    blockingStub = GreeterGrpc.newBlockingStub(channel);
  }

    private static SslContext buildSslContext(String trustCertCollectionFilePath,
                                              String clientCertChainFilePath,
                                              String clientPrivateKeyFilePath) throws SSLException {
        SslContextBuilder builder = GrpcSslContexts.forClient();
        if (trustCertCollectionFilePath != null) {
            builder.trustManager(new File(trustCertCollectionFilePath));
        }
        if (clientCertChainFilePath != null && clientPrivateKeyFilePath != null) {
            builder.keyManager(new File(clientCertChainFilePath), new File(clientPrivateKeyFilePath));
        }
        return builder.build();
    }

  public void shutdown() throws InterruptedException {
    managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /** Say hello to server. */
  public void greet(String name) {
    //logger.info("Will try to greet " + name + " ...");
    HelloRequest request = HelloRequest.newBuilder().setName(name).build();
    HelloReply response;
    try {
      response = blockingStub.sayHello(request);
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      return;
    }
    System.out.println("Greeting: " + response.getMessage() + ", from " + interceptor.callRef.get().getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR));
  }

  /**
   * Greet server. If provided, the first element of {@code args} is the name to use in the
   * greeting.
   */
  public static void main(String[] args) throws Exception {
String loggingConfig =
    "handlers=java.util.logging.ConsoleHandler\n"
        + "io.grpc.level=FINE\n"
        + "java.util.logging.ConsoleHandler.level=FINE\n"
        + "java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter";
java.util.logging.LogManager.getLogManager()
    .readConfiguration(
        new java.io.ByteArrayInputStream(
            loggingConfig.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    String sslCertFile = null;
    if (args.length == 3) {
      sslCertFile = args[2];
    }
    HelloWorldClient client = new HelloWorldClient(args[0], buildSslContext(sslCertFile, null, null));
    int secRemaining = 1;
    if (args.length > 1) {
      secRemaining = Integer.parseInt(args[1]);
    }
    try {
      while (secRemaining-- > 0) {
        String user = "world";
        client.greet(user);
        TimeUnit.SECONDS.sleep(1);
      }
    } finally {
        client.shutdown();
    }
  }

 private static class HeaderClientInterceptor implements ClientInterceptor {
  private final AtomicReference<ClientCall<?, ?>> callRef = new AtomicReference<ClientCall<?, ?>>();
  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
      CallOptions callOptions, Channel next) {
    ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
    callRef.set(call);
    return call;
  }
}
}
