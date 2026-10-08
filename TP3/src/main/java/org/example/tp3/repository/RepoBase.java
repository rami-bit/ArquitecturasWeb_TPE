package org.example.tp3.repository;


import org.example.tp3.model.Estudiante;
import org.springframework.data.repository.NoRepositoryBean;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface RepoBase<T,ID extends Serializable> extends org.springframework.data.repository.Repository<T,ID> {

    List<T> findAll();

    Optional<T> findById(Long id);

    T save( T persisted);
}
