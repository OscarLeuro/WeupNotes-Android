# Contrato pendiente: WeupNotes móvil ↔ servidor

**Estado:** diseño técnico pendiente de implementación y pruebas contra backend WeupNotes v1.0.8 o superior. No constituye una API pública existente. No desplegar contra datos reales sin validar este contrato.

Para habilitar edición de notas del servidor sin Internet, proporcionar el ZIP COMPLETO del último backend instalado (api/, web/, migraciones, documentación; sin secretos .env ni copias de datos privados). Se requieren:

1. Endpoint autenticado para identificar usuario activo y permisos, sin exponer claves en Android; enlace de sesión entre WebView y cliente nativo con protección CSRF.
2. Lectura incremental/paginada de espacios, libretas, notas y versiones/ETag por usuario; almacenar localmente solo notas autorizadas y cifradas (con partición por usuario para prevenir fuga entre sesiones).
3. Escritura idempotente por UUID de operación y baseRevision/If-Match; respuestas que confirmen ID/version y conflicto 409/412. Nunca sobrescribir silenciosamente una edición remota.
4. Cola local persistente de CREATE/UPDATE/DELETE, reintentos exponenciales y estados pendiente, sincronizado, error y conflicto; revalidar identidad/permisos antes de subir; nunca eliminar original hasta confirmación transaccional.
5. Resolución de conflictos mediante copias comparables (local/servidor), opción elegir versión o duplicar para conservar ambas, historial y tests para dos dispositivos.
6. Estrategia de adjuntos, límites, borrado remoto y sesión expirada; sincronización al recuperar conectividad vía WorkManager, con ejecución manual alternativa.
7. En la interfaz web, evento de acceso rápido `weupnotes:native-action` para que las cuatro acciones funcionen sobre rutas y modales estables, sin depender del texto de los botones.

Mientras ese contrato no se implemente, **offline = borradores locales independientes**. Los widgets muestran únicamente datos locales, con vistas previas opt-in. Nunca se mostrarán borradores como sincronizados.
