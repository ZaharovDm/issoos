Issoos
=====================
Example integration spring secuirty with Openshift Oauth Server.

В данном приложение реализована интеграция oauth spring security с oauth сервером openshift-a. При попытке открыть UI
приложения, пользователю будет открыта стандартная форма логина от openshift-a.

![Openshift login page](https://www.openshift.com/hubfs/Google%20Drive%20Integration/Enhancing%20the%20OpenShift%20Web%20Console%20Login%20Experience-1.png)


При успешной аутентификации пользователя, oauth-server автоматически сделает redirect уже на приложение.


***
Особенности реализации
-------------
В этом проекте добавлены необходимые зависимости, для работоспособности интеграции. А также в пакете:

```java
com.github.zaharovdm.secuirty.oauth.openshift
```

добавлены кастомные реализации следующих сущностей spring-a:

- org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
- org.springframework.security.oauth2.client.OAuth2UserService


В результата из SecurityContextHolder.getContext().getAuthentication(), можно получить token openshift-a.
С которым, уже можно будет обращаться в kube api.

Пример получения kubeClient, можно посмотреть в классе:
```java
com.github.zaharovdm.kube.KubeClientProvider
```

***
Конфигурация приложения
-------------------

```yaml
  application.yaml: |
    issoos:
      client:
        id: issoos-oauth-client                (должен совпадать с именем сущности openshift-a OauthClient)
        secret: test                           (секретный ключ должен совпадать с ключом, который указан в сущности openshift-a OauthClient)
      kube:
        route:
          suffix: apps-crc.testing             (имя кластера openshift-a, который добавляется в конце маршрута openshift-a)
          url: https://api.crc.testing:6443    (адрес kube api)

```

***
Сущность на уровне всего кластера openshift-a "OauthClient"
--------------

```yaml
kind: OAuthClient
apiVersion: oauth.openshift.io/v1
metadata:
  name: issoos-oauth-client
secret: test
redirectURIs:
  - 'https://issos-zaharovdm.apps-crc.testing/'                  (имя host-a, за которым запущено ваше приложение)
grantMethod: auto
```

Подробнее:
https://docs.openshift.com/container-platform/4.6/authentication/configuring-oauth-clients.html

```yaml
kind: Route
apiVersion: route.openshift.io/v1
metadata:
  name: issos
spec:
  host: issos-zaharovdm.apps-crc.testing                        (это значение как раз указывается, в сущности OAuthClient, в поле redirectURIs)
  to:
    kind: Service
    name: issos
    weight: 100
  port:
    targetPort: http
  tls: (обязательно включаем tls для маршрута, с функцией Redirect)
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
  wildcardPolicy: None
```

Пример вывода программы, при успешно пройденной аутентификации, на Code ready containers, под учеткой kubeadmin:

```text
[{"name":"default","podDTOList":[],"routeDTOList":[],"
serviceDTOList":[{"selectors":null,"name":"kubernetes"},{"selectors":null,"name":"openshift"}]},{"name":"
kube-node-lease","podDTOList":[],"routeDTOList":[],"serviceDTOList":[]},{"name":"kube-public","podDTOList":[],"
routeDTOList":[],"serviceDTOList":[]},{"name":"kube-system","podDTOList":[],"routeDTOList":[],"serviceDTOList":[]},{"
name":"openshift","podDTOList":[],"routeDTOList":[],"serviceDTOList":[]},{"name":"openshift-apiserver","
podDTOList":[{"name":"apiserver-575748955f-kj962","status":"RUNNING"}],"routeDTOList":[],"
serviceDTOList":[{"selectors":{"apiserver":"true"},"name":"api"},{"selectors":{"apiserver":"true"},"name":"check-endpoints"}]
},.......
serviceDTOList":[{"selectors":{"app":"catalog-operator"},"name":"catalog-operator-metrics"},{"selectors":{"app":"olm-operator"},"name":"olm-operator-metrics"},{"selectors":{"app":"packageserver"},"name":"packageserver-service"}]
},{"name":"openshift-operators","podDTOList":[],"routeDTOList":[],"serviceDTOList":[]},{"name":"openshift-ovirt-infra","
podDTOList":[],"routeDTOList":[],"serviceDTOList":[]},{"name":"openshift-sdn","
podDTOList":[{"name":"sdn-7c4xq","status":"RUNNING"},{"name":"sdn-controller-6vmmh","status":"RUNNING"}],"
routeDTOList":[],"serviceDTOList":[{"selectors":{"app":"sdn"},"name":"sdn"}]},{"name":"openshift-service-ca","
podDTOList":[{"name":"service-ca-5bcbd85c69-f2ltb","status":"RUNNING"}],"routeDTOList":[],"serviceDTOList":[]},{"name":"
openshift-service-ca-operator","podDTOList":[{"name":"service-ca-operator-fc7cc84b8-kpr99","status":"RUNNING"}],"
routeDTOList":[],"serviceDTOList":[{"selectors":{"app":"service-ca-operator"},"name":"metrics"}]},{"name":"
openshift-user-workload-monitoring","podDTOList":[],"routeDTOList":[],"serviceDTOList":[]},{"name":"
openshift-vsphere-infra","podDTOList":[],"routeDTOList":[],"serviceDTOList":[]},{"name":"zaharovdm","
podDTOList":[{"name":"issos-6ddcfd77c5-8vsr7","status":"RUNNING"}],"
routeDTOList":[{"name":"issos","host":"issos-zaharovdm.apps-crc.testing"}],"
serviceDTOList":[{"selectors":{"app":"issos"},"name":"issos"}]}]
```

А это вывод из под пользователя developer, у которого есть доступ только к проекту "zaharovdm":

```text
[{"name":"zaharovdm","podDTOList":[{"name":"issos-6ddcfd77c5-8vsr7","status":"RUNNING"}],"
routeDTOList":[{"name":"issos","host":"issos-zaharovdm.apps-crc.testing"}],"
serviceDTOList":[{"selectors":{"app":"issos"},"name":"issos"}]}]
```

***
Что есть что в полученных Authorization Request Parameters
---------------------
response_type=code 
client_id=issoos-oauth-client - имя сущности созданной сущности OauthClient
redirect_uri=https://issos-zaharovdm.apps-crc.testing/ маршрут нашего приложения
scope=user:full; user:check-access; user:list-projects"   -   openshift oauth server поддерживает не все scope указанные в RFC 6749

***
Полезные ссылки:
------------------
- https://developers.redhat.com/products/codeready-containers/overview Code ready containers (локальный кластер openshift-a)
- https://docs.openshift.com/container-platform/4.6/authentication/configuring-oauth-clients.html Статья о сущности openshift-a OauthClient
- https://docs.openshift.com/container-platform/4.1/authentication/configuring-internal-oauth.html Статья о настройках Oauth server openshift-a
- https://docs.spring.io/spring-security/site/docs/current/reference/html5/#oauth2 документация spring security