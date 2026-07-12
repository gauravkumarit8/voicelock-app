package com.voicelock.app.data;

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
public final class VoiceprintStore_Factory implements Factory<VoiceprintStore> {
  private final Provider<Context> contextProvider;

  public VoiceprintStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public VoiceprintStore get() {
    return newInstance(contextProvider.get());
  }

  public static VoiceprintStore_Factory create(Provider<Context> contextProvider) {
    return new VoiceprintStore_Factory(contextProvider);
  }

  public static VoiceprintStore newInstance(Context context) {
    return new VoiceprintStore(context);
  }
}
