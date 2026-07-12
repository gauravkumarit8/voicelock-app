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
public final class SpeakerVerificationEngine_Factory implements Factory<SpeakerVerificationEngine> {
  private final Provider<Context> contextProvider;

  public SpeakerVerificationEngine_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SpeakerVerificationEngine get() {
    return newInstance(contextProvider.get());
  }

  public static SpeakerVerificationEngine_Factory create(Provider<Context> contextProvider) {
    return new SpeakerVerificationEngine_Factory(contextProvider);
  }

  public static SpeakerVerificationEngine newInstance(Context context) {
    return new SpeakerVerificationEngine(context);
  }
}
