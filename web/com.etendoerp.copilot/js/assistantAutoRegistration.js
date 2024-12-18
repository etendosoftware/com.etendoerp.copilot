OB.AssistantAutoRegistration = {};
OB.AssistantAutoRegistration.ClientSideEventHandlersPostSaveUpdate = {};

OB.AssistantAutoRegistration.ETCOP_APP_TAB = 'F0AE228DDA0D4A3F98A08B8284EF1689';

OB.AssistantAutoRegistration.ClientSideEventHandlersPostSaveUpdate = function (view, form, grid, extraParameters, actions) {
  const data = extraParameters.data;

  const callback = function (response, data, request) {
    OB.EventHandlerRegistry.callbackExecutor(view, form, grid, extraParameters, actions);
  };

  OB.RemoteCallManager.call(
    'com.etendoerp.copilot.eventhandler.AutoRegistrationAssistantHandler',
    {
      appId: data.id
    },
    {},
    callback
  );
};

OB.EventHandlerRegistry.register(
  OB.AssistantAutoRegistration.ETCOP_APP_TAB,
  OB.EventHandlerRegistry.POSTSAVE,
  OB.AssistantAutoRegistration.ClientSideEventHandlersPostSaveUpdate,
  'OBAssistantAutoRegistration'
);
