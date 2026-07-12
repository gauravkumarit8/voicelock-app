package com.voicelock.app.ml;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class WakeWordEngine_Factory implements Factory<WakeWordEngine> {
  private final Provider<Context> contextProvider;

  public WakeWordEngine_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public WakeWordEngine get() {
    return newInstance(contextProvider.get());
  }

  public static WakeWordEngine_Factory create(Provider<Context> contextProvider) {
    return new WakeWordEngine_Factory(contextProvider);
  }

  public static WakeWordEngine newInstance(Context context) {
    return new WakeWordEngine(context);
  }
}
