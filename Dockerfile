FROM openjdk:11.0.13

# Recommended setting for Crux
ENV MALLOC_ARENA_MAX=2

RUN apt-get update && \
    apt install -y build-essential rlwrap

# Pull and prepare Clojure
RUN curl -O https://download.clojure.org/install/linux-install-1.10.3.822.sh
RUN chmod +x linux-install-1.10.3.822.sh
RUN ./linux-install-1.10.3.822.sh

EXPOSE 51585
EXPOSE 40404
WORKDIR /usr/src/app

ENTRYPOINT ["make", "run"]