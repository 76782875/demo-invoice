package com.oxchains.billing.rest;

import com.oxchains.billing.notification.PushService;
import com.oxchains.billing.rest.common.ChaincodeUriBuilder;
import com.oxchains.billing.rest.common.ClientResponse2ServerResponse;
import com.oxchains.billing.rest.common.GuaranteeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import static com.oxchains.billing.domain.BillActions.*;
import static com.oxchains.billing.rest.common.ClientResponse2ServerResponse.toPayloadTransformedServerResponse;
import static com.oxchains.billing.rest.common.ClientResponse2ServerResponse.toServerResponse;
import static com.oxchains.billing.util.ArgsUtil.args;
import static com.oxchains.billing.util.ResponseUtil.chaincodeInvoke;
import static com.oxchains.billing.util.ResponseUtil.chaincodeQuery;
import static org.springframework.web.reactive.function.server.ServerResponse.badRequest;
import static org.springframework.web.reactive.function.server.ServerResponse.noContent;

/**
 * @author aiet
 */
@Component
public class GuarantyHandler extends ChaincodeUriBuilder {

  private PushService pushService;

  public GuarantyHandler(@Autowired WebClient client,
                         @Autowired @Qualifier("fabric.uri") UriBuilder uriBuilder,
                         @Autowired PushService pushService) {
    super(client, uriBuilder.build().toString());
    this.pushService = pushService;
  }

  /* POST /bill/guaranty */
  public Mono<ServerResponse> create(ServerRequest request) {
    return request.bodyToMono(GuaranteeAction.class)
        .flatMap(guaranteeAction -> chaincodeInvoke(client, buildUri(args(BILL_GUARANTY, guaranteeAction)))
            .flatMap(clientResponse -> {
              if (guaranteeAction.getAction() == null) {
                pushService.sendMsg(guaranteeAction.getGuarantor(), "请担保: 汇票" + guaranteeAction.getId());
              }
              return Mono.just(toServerResponse(clientResponse));
            })
            .switchIfEmpty(noContent().build())
        ).switchIfEmpty(badRequest().build());
  }

  /* PUT /bill/guaranty */
  public Mono<ServerResponse> update(ServerRequest request) {
    return request.bodyToMono(GuaranteeAction.class)
        .flatMap(guaranteeAction -> chaincodeInvoke(client, buildUri(args(BILL_GUARANTY, guaranteeAction)))
            .flatMap(clientResponse -> chaincodeQuery(client, buildUri(args(GET, "BillStruct" + guaranteeAction.getId())))
                .flatMap(billResponse ->
                    ((ClientResponse2ServerResponse) toPayloadTransformedServerResponse(billResponse))
                        .billSink().flatMap(bill -> {
                      if ("1".equals(guaranteeAction.getAction())) {
                        pushService.sendMsg(bill.getDrawee(), "已担保: 汇票" + guaranteeAction.getId());
                      }
                      return Mono.just(toServerResponse(clientResponse));
                    })
                )
            ).switchIfEmpty(noContent().build())
        ).switchIfEmpty(badRequest().build());
  }

  public Mono<ServerResponse> get(ServerRequest request) {
    final String uid = request.pathVariable("uid");
    return chaincodeQuery(client, buildUri(args(GET_GUARANTY, uid)))
        .flatMap(clientResponse -> Mono.just(toPayloadTransformedServerResponse(clientResponse)))
        .switchIfEmpty(noContent().build());
  }

}
