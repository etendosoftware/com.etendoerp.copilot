package com.etendoerp.copilot.engine.tool.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.junit.Test;

public class EtendoHttpTest {

  @Test
  public void sameEtendoHostIncludesEquivalentDefaultPorts() {
    assertTrue(EtendoHttp.isSameHost(URI.create("https://erp.example/sws/a"),
        URI.create("https://erp.example:443/etendo")));
    assertFalse(EtendoHttp.isSameHost(URI.create("https://erp.example:8443/sws/a"),
        URI.create("https://erp.example/etendo")));
    assertFalse(EtendoHttp.isSameHost(URI.create("https://other.example/sws/a"),
        URI.create("https://erp.example/etendo")));
  }

  @Test
  public void redirectAuthorizationScopeRequiresSameSchemeHostAndPort() {
    assertTrue(EtendoHttp.isSameOrigin(URI.create("https://erp.example/one"),
        URI.create("https://erp.example:443/two")));
    assertFalse(EtendoHttp.isSameOrigin(URI.create("https://erp.example/one"),
        URI.create("http://erp.example/one")));
    assertFalse(EtendoHttp.isSameOrigin(URI.create("https://erp.example/one"),
        URI.create("https://other.example/one")));
  }

  @Test
  public void redirectsCannotGainEtendoIdentity() {
    URI etendo = URI.create("https://erp.example/etendo");
    URI external = URI.create("https://other.example/callback");
    assertTrue(EtendoHttp.mayAttachIdentity(true, etendo, etendo));
    assertFalse(EtendoHttp.mayAttachIdentity(false, etendo, etendo));
    assertFalse(EtendoHttp.mayAttachIdentity(true, external, etendo));
  }

  @Test
  public void pathJoiningPreservesConfiguredEtendoContextPath() {
    assertEquals("https://erp.example/etendo/webhooks/?name=ReadOAuthToken",
        EtendoHttp.appendPath("https://erp.example/etendo/", "/webhooks/?name=ReadOAuthToken"));
  }

  @Test
  public void tokenPlaceholderReplacementDoesNotChangeOtherHeaderText() {
    assertEquals("Bearer freshly-minted", EtendoHttp.replaceTokenPlaceholder("Bearer ETENDO_TOKEN", "freshly-minted"));
    assertEquals("Basic abc", EtendoHttp.replaceTokenPlaceholder("Basic abc", "freshly-minted"));
  }
}
