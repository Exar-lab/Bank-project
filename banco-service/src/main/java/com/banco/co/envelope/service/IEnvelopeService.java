package com.banco.co.envelope.service;

import com.banco.co.envelope.dto.*;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;
import com.banco.co.envelope.model.Envelope;

import java.time.LocalDateTime;
import java.util.List;

public interface IEnvelopeService {
    // ══════════════════════════════════════════════════════════
    //  CREAR
    // ══════════════════════════════════════════════════════════

    /**
     * Crear un envelope para una cuenta del usuario
     *
     * @param dto Datos del envelope a crear
     * @param userEmail Email del usuario autenticado
     * @return Envelope creado
     */
    EnvelopeResponseDto create(EnvelopeRequestDto dto, String userEmail);

    // ══════════════════════════════════════════════════════════
    //  CONSULTAR
    // ══════════════════════════════════════════════════════════

    /**
     * Obtener un envelope activo por su código
     *
     * @param envelopeCode Código del envelope
     * @param userEmail Email del usuario autenticado
     * @return Envelope encontrado
     */
    EnvelopeResponseDto getActiveEnvelope(String envelopeCode, String userEmail);

    /**
     * Obtener todos los envelopes activos del usuario autenticado
     *
     * @param userEmail Email del usuario autenticado
     * @return Lista de envelopes del usuario
     */
    List<EnvelopeResponseDto> getMyEnvelopes(String userEmail);

    /**
     * Obtener todos los envelopes activos de una cuenta específica
     *
     * @param accountCode Código de la cuenta
     * @param userEmail Email del usuario autenticado
     * @return Lista de envelopes de la cuenta
     */
    List<EnvelopeResponseDto> getActiveAllByAccountCode(String accountCode, String userEmail);

    /**
     * Obtener envelopes del usuario por status
     *
     * @param status Status del envelope
     * @param userEmail Email del usuario autenticado
     * @return Lista de envelopes con el status especificado
     */
    List<EnvelopeResponseDto> findAllByStatus(EnvelopeStatus status, String userEmail);

    /**
     * Obtener envelopes activos del usuario por tipo
     *
     * @param type Tipo de envelope
     * @param userEmail Email del usuario autenticado
     * @return Lista de envelopes del tipo especificado
     */
    List<EnvelopeResponseDto> findAllActiveByType(EnvelopeType type, String userEmail);

    /**
     * Obtener envelopes de una cuenta filtrados por status
     *
     * @param status Status del envelope
     * @param accountCode Código de la cuenta
     * @param userEmail Email del usuario autenticado
     * @return Lista de envelopes
     */
    List<EnvelopeResponseDto> findAllByStatusAndAccountCode(
            EnvelopeStatus status,
            String accountCode,
            String userEmail
    );

    /**
     * Obtener envelopes creados después de una fecha
     *
     * @param createdAfter Fecha límite
     * @param accountCode Código de la cuenta
     * @param userEmail Email del usuario autenticado
     * @return Lista de envelopes creados después de la fecha
     */
    List<EnvelopeResponseDto> getActiveByCreatedAfter(
            LocalDateTime createdAfter,
            String accountCode,
            String userEmail
    );

    // ══════════════════════════════════════════════════════════
    //  ACTUALIZAR
    // ══════════════════════════════════════════════════════════

    /**
     * Actualizar datos de un envelope
     *
     * @param dto Datos a actualizar
     * @param envelopeCode Código del envelope
     * @param userEmail Email del usuario autenticado
     * @return Envelope actualizado
     */
    EnvelopeResponseDto update(EnvelopeUpdateDto dto, String envelopeCode, String userEmail);

    /**
     * Actualizar el status de un envelope
     *
     * @param envelopeCode Código del envelope
     * @param status Nuevo status
     * @param userEmail Email del usuario al que se le cambia el sobre
     * @return Envelope actualizado
     */
    EnvelopeResponseDto updateStatusByAdmin(String envelopeCode, EnvelopeStatus status,String userEmail);

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES DE SALDO
    // ══════════════════════════════════════════════════════════

    /**
     * Depositar dinero en un envelope
     *
     * @param dto Datos de depósito
     * @param userEmail Email del usuario autenticado
     * @return Envelope actualizado con el nuevo balance
     */
    EnvelopeResponseDto deposit(EnvelopeDepositDto dto, String userEmail);

    /**
     * Retirar dinero de un envelope
     *
     * @param dto Datos de retiro de dinero
     * @param userEmail Email del usuario autenticado
     * @return Envelope actualizado con el nuevo balance
     */
    EnvelopeResponseDto withdraw(EnvelopeWithdrawDto dto, String userEmail);

    // ══════════════════════════════════════════════════════════
    //  ELIMINAR
    // ══════════════════════════════════════════════════════════

    /**
     * Eliminar un envelope (soft delete)
     * Solo se puede eliminar si el balance es 0
     *
     * @param envelopeCode Código del envelope
     * @param userEmail Email del usuario autenticado
     */
    void delete(String envelopeCode, String userEmail);

    // ══════════════════════════════════════════════════════════
    //  MÉTODOS AUXILIARES (Para uso interno)
    // ══════════════════════════════════════════════════════════

    /**
     * Obtener entidad Envelope por código (para uso interno)
     *
     * @param envelopeCode Código del envelope
     * @return Entidad Envelope
     */
    Envelope findActiveByEnvelopeCode(String envelopeCode);

    /**
     * Obtener entidad Envelope con Account cargado (para uso interno)
     *
     * @param envelopeCode Código del envelope
     * @return Entidad Envelope con Account
     */
    Envelope findActiveWithAccountByCode(String envelopeCode);
}
