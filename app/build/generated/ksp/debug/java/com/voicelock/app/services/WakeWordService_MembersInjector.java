package com.voicelock.app.services;

import com.voicelock.app.ml.WakeWordEngine;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class WakeWordService_MembersInjector implements MembersInjector<WakeWordService> {
  private final Provider<WakeWordEngine> wakeWordEngineProvider;

  public WakeWordService_MembersInjector(Provider<WakeWordEngine> wakeWordEngineProvider) {
    this.wakeWordEngineProvider = wakeWordEngineProvider;
  }

  public static MembersInjector<WakeWordService> create(
      Provider<WakeWordEngine> wakeWordEngineProvider) {
    return new WakeWordService_MembersInjector(wakeWordEngineProvider);
  }

  @Override
  public void injectMembers(WakeWordService instance) {
    injectWakeWordEngine(instance, wakeWordEngineProvider.get());
  }

  @InjectedFieldSignature("com.voicelock.app.services.WakeWordService.wakeWordEngine")
  public static void injectWakeWordEngine(WakeWordService instance, WakeWordEngine wakeWordEngine) {
    instance.wakeWordEngine = wakeWordEngine;
  }
}
