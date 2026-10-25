-- =============================================================================
-- V3: integridad referencial, dominios e índices
-- =============================================================================
-- Declara como llaves foráneas las relaciones que hasta V2 solo existían en el
-- código, cierra el dominio de recorrido.estado y recorrido.nivel, vuelve
-- obligatorias las columnas que el código siempre llena, le da llave primaria
-- a observacion_opciones y crea los índices de las consultas de los DAO.
--
-- observacion_entity es anterior a la tabla recorrido: una base con historia
-- puede tener observaciones cuyo recorrido no existe. Esa llave foránea se
-- crea NOT VALID para que se exija en las filas nuevas sin borrar las viejas.
-- =============================================================================

-- Dominios y obligatoriedad ---------------------------------------------------

alter table recorrido
    add constraint ck_recorrido_estado
        check (estado in ('EN_CURSO', 'FINALIZADO', 'VENCIDO', 'ABANDONADO')),
    add constraint ck_recorrido_nivel
        check (nivel in ('NORMAL', 'CONSULTAR_MEDICO', 'URGENCIA'));

alter table observacion_entity
    alter column recorrido_id set not null,
    alter column tipo set not null,
    alter column timestamp set not null;

-- Llave primaria de las opciones: dueño + posición en la lista ----------------

alter table observacion_opciones add column orden integer;

update observacion_opciones o
   set orden = n.fila - 1
  from (select ctid, row_number() over (partition by observacion_id order by ctid) as fila
          from observacion_opciones) n
 where o.ctid = n.ctid;

alter table observacion_opciones
    alter column orden set not null,
    add constraint pk_observacion_opciones primary key (observacion_id, orden);

-- Llaves foráneas -------------------------------------------------------------

alter table recorrido
    add constraint fk_recorrido_usuario
        foreign key (usuario_id) references usuario (id);

alter table observacion_entity
    add constraint fk_observacion_recorrido
        foreign key (recorrido_id) references recorrido (id) not valid,
    add constraint fk_observacion_usuario
        foreign key (usuario_id) references usuario (id);

alter table borrador_subgrafo_entity
    add constraint fk_borrador_subgrafo_autor
        foreign key (autor_id) references usuario (id),
    add constraint fk_borrador_subgrafo_revisor
        foreign key (revisor_id) references usuario (id);

alter table borrador_base_regla_entity
    add constraint fk_borrador_base_regla_autor
        foreign key (autor_id) references usuario (id);

-- Índices de las consultas de los DAO -----------------------------------------
-- (las PK y las restricciones unique ya traen el suyo)

-- RepositorioRecorridos.findByUsuarioIdOrderByIniciadoEnDesc
create index ix_recorrido_usuario on recorrido (usuario_id, iniciado_en desc);

-- RepositorioObservaciones.findByRecorridoIdOrderByTimestampAsc
create index ix_observacion_recorrido on observacion_entity (recorrido_id, timestamp);

-- Respaldo de fk_observacion_usuario
create index ix_observacion_usuario on observacion_entity (usuario_id);

-- RepositorioBorradorSubgrafo.findByEstado (bandeja de revisión)
create index ix_borrador_subgrafo_estado on borrador_subgrafo_entity (estado);

-- RepositorioBorradorSubgrafo.findByAutorId y RepositorioBorradorBaseRegla.findByAutorId
create index ix_borrador_subgrafo_autor on borrador_subgrafo_entity (autor_id);
create index ix_borrador_base_regla_autor on borrador_base_regla_entity (autor_id);

-- Respaldo de fk_borrador_subgrafo_revisor
create index ix_borrador_subgrafo_revisor on borrador_subgrafo_entity (revisor_id);
