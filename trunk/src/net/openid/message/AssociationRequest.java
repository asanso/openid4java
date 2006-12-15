/*
 * Copyright 2006 Sxip Identity Corporation
 */

package net.openid.message;

import net.openid.association.DiffieHellmanSession;
import net.openid.association.AssociationSessionType;
import net.openid.association.AssociationException;

import java.util.List;
import java.util.Arrays;

/**
 * The OpenID Association Request message.
 * <p>
 * Handles OpenID 2.0 and OpenID 1.x messages.
 *
 * @see AssociationSessionType
 * @author Marius Scurtescu, Johnny Bufu
 */
public class AssociationRequest extends Message
{
    public static final String MODE_ASSOC = "associate";

    protected final static List requiredFields = Arrays.asList( new String[] {
            "openid.mode",
            "opneid.session_type",
    });

    protected final static List optionalFields = Arrays.asList( new String[] {
            "openid.ns",                    // not in v1 messages
            "openid.assoc_type",            // can be missing in v1
            "openid.dh_modulus",
            "openid.dh_gen",
            "openid.dh_consumer_public"
    });

    /**
     * The Diffie-Hellman session containing the cryptografic data needed for
     * encrypting the MAC key exchange.
     * <p>
     * Null for no-encryption sessions.
     */
    private DiffieHellmanSession _dhSess;


    /**
     * Creates an Association Request message with the
     * specified association type and "no-encryption" session.
     * <p>
     * The supplied type must be one of the "no-encryption" types, otherwise
     * a DiffieHellman session is required.
     *
     * @see #AssociationRequest(AssociationSessionType, DiffieHellmanSession)
     */
    protected AssociationRequest(AssociationSessionType type)
    {
        this(type, null);
    }

    /**
     * Constructs an AssociationRequest message with the
     * specified association type and Diffie-Hellman session.
     *
     * @param dhSess    Diffie-Hellman session to be used for this association;
     *                  if null, a "no-encryption" session is created.
     */
    protected AssociationRequest(AssociationSessionType type,
                              DiffieHellmanSession dhSess)
    {
        if (type.isVersion2())
            set("openid.ns", OPENID2_NS);

        set("openid.mode", MODE_ASSOC);
        set("openid.session_type", type.getSessionType());
        set("openid.assoc_type", type.getAssociationType());

        _dhSess = dhSess;

        if (dhSess != null )
        {
            set("openid.dh_modulus", _dhSess.getModulus());
            set("openid.dh_gen", _dhSess.getGenerator());
            set("openid.dh_consumer_public", _dhSess.getPublicKey());
        }
    }

    /**
     * Constructs an AssociationRequest message from a parameter list.
     * <p>
     * Useful for processing incoming messages.
     */
    protected AssociationRequest(ParameterList params)
    {
        super(params);
    }

    public static AssociationRequest createAssociationRequest(
            AssociationSessionType type) throws MessageException
    {
        return createAssociationRequest(type, null);
    }

    public static AssociationRequest createAssociationRequest(
            AssociationSessionType type, DiffieHellmanSession dhSess)
            throws MessageException
    {
        AssociationRequest req = new AssociationRequest(type, dhSess);

        // make sure the association / session type matches the dhSess
        if ( type == null ||
                (dhSess == null && type.getHAlgorithm() != null) ||
                (dhSess != null && ! dhSess.getType().equals(type) ) )
            throw new MessageException(
                    "Invalid association / session combination specified: " +
                            type + "DH session: " + dhSess);

        if ( !req.isValid() ) throw new MessageException(
                "Invalid set of parameters for the requested message type");

        return req;
    }
    public static AssociationRequest createAssociationRequest(
            ParameterList params) throws MessageException
    {
        AssociationRequest req = new AssociationRequest(params);

        if ( !req.isValid() ) throw new MessageException(
                "Invalid set of parameters for the requested message type");

        return req;
    }

    public List getRequiredFields()
    {
        return requiredFields;
    }

    /**
     * Returns true for OpenID 2.0 messages, false otherwise.
     */
    public boolean isVersion2()
    {
        return hasParameter("openid.ns") &&
                OPENID2_NS.equals(getParameterValue("openid.ns"));
    }

    /**
     * Gets the association type parameter of the message.
     */
    private String getAssociationType()
    {
        return getParameterValue("openid.assoc_type");
    }

    /**
     * Gets the session type parameter of the message.
     */
    private String getSessionType()
    {
        return getParameterValue("openid.session_type");
    }

    /**
     * Gets the association / session type of the association request.
     *
     * @throws AssociationException
     */
    public AssociationSessionType getType() throws AssociationException
    {
        return AssociationSessionType.create(
                getSessionType(), getAssociationType(), ! isVersion2() );
    }

    /**
     * Gets the Diffie-Hellman session
     * Null for no-encryption association requests.
     */
    public DiffieHellmanSession getDHSess()
    {
        return _dhSess;
    }

    /**
     * Gets the Diffie-Hellman modulus parameter of the message, or null for
     * messages with no-encryption sessions.
     */
    public String getDhModulus()
    {
        return getParameterValue("openid.dh_modulus");
    }

    /**
     * Gets the Diffie-Hellman generator parameter of the message, or null for
     * messages with no-encryption sessions.
     */
    public String getDhGen()
    {
        return getParameterValue("openid.dh_gen");

    }

    /**
     * Gets the Relying Party's (consumer) Diffie-Hellman public key, or null
     * for messages with no-encryption sessions.
     */
    public String getPublicKey()
    {
        return getParameterValue("openid.dh_consumer_public");
    }

    /**
     * Checks if the message is a valid OpenID Association Request.
     *
     * @return True if all validation checkes passed, false otherwise.
     */
    public boolean isValid()
    {
        // basic checks
        if (! super.isValid()) return false;

        // association / session type checks
        // (includes most of the compatibility stuff)
        AssociationSessionType type;
        try
        {
            // throws exception for invalid session / association types
            type = getType();

            // make sure compatibility mode is the same for type and message
            if (type.isVersion2() != isVersion2())
                return false;

        } catch (AssociationException e) {
            return false;
        }

        // additional compatibility checks
        if (! isVersion2() && getSessionType() == null)
                return false;   // sess_type cannot be omitted in v1 requests

        // DH seesion parameters
        if ( type.getHAlgorithm() != null) // DH session
        {
            if (getDhGen() == null || getDhModulus() == null ||
                    getPublicKey() == null)
                return false;
        }
        else // no-enc session
        {
            if (getDhGen() != null || getDhModulus() != null ||
                    getPublicKey() != null)
                return false;
        }

        return true;
    }
}